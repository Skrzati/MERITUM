package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.domain.PreInterviewSurvey.Status;
import pl.gov.kprm.oceny.repository.PreInterviewSurveyRepository;
import pl.gov.kprm.oceny.repository.SchemaVersionRepository;
import pl.gov.kprm.oceny.rules.RuleViolationException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Ankieta przygotowawcza przed rozmową (§ 5 ust. 1 / § 10): pytania ze schematu ({@code config.preInterviewSurvey})
 * uzupełniane pytaniami własnymi kadr lub przełożonego, odpowiedzi ocenianego trafiają do przełożonego po wysłaniu.
 * Kryteria własne arkusza (v1: {@code criteriaRules.allowCustom=false}) to osobna reguła — ankieta ich nie zmienia.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PreInterviewSurveyService {
    static final int MAX_CUSTOM_QUESTIONS = 10;
    static final int MAX_QUESTION_LENGTH = 500;
    static final int MAX_ANSWER_LENGTH = 5000;
    static final List<String> DEFAULT_QUESTIONS = List.of(
            "Jakie zadania i osiągnięcia z okresu oceny uważa Pani/Pan za najważniejsze?",
            "Jakie trudności lub przeszkody wpływały na realizację zadań?",
            "Jakiego wsparcia od przełożonego lub zespołu potrzebuje Pani/Pan?",
            "Jakie kompetencje chciałaby Pani/chciałby Pan rozwijać (propozycje do IPRZ)?",
            "Jakie tematy chce Pani/Pan poruszyć podczas rozmowy?");

    private final CycleWorkflowService workflow;
    private final PreInterviewSurveyRepository surveys;
    private final SchemaVersionRepository schemas;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public record Question(String id, String text, boolean custom, Long addedBy, String answer) {
        Question withAnswer(String a) {
            return new Question(id, text, custom, addedBy, a);
        }
    }

    /** {@code answersVisible = false} — przełożony przed wysłaniem albo kadry: pytania bez odpowiedzi. */
    public record SurveyView(Long cycleId, Status status, OffsetDateTime sentAt, boolean allowCustom,
                             boolean answersVisible, List<Question> questions) {
    }

    @Transactional(readOnly = true)
    public SurveyView view(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        Role access = accessOf(cycle, user);
        PreInterviewSurvey survey = surveys.findByCycleId(cycleId).orElseGet(() -> fresh(cycle));
        boolean answersVisible = access == Role.EVALUATED
                || (access == Role.EVALUATOR && survey.getStatus() == Status.SENT);
        List<Question> questions = questions(survey).stream()
                .map(q -> answersVisible ? q : q.withAnswer(null)).toList();
        return new SurveyView(cycleId, survey.getStatus(), survey.getSentAt(), allowCustom(cycle), answersVisible,
                questions);
    }

    /** Pytanie własne dodaje kadra lub przełożony (zastępca), dopóki oceniany nie wysłał ankiety. */
    public SurveyView addQuestion(Long cycleId, User user, String text) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        Role access = accessOf(cycle, user);
        if (access != Role.EVALUATOR && access != Role.HR) {
            throw forbidden("Pytania własne dodaje przełożony lub kadry");
        }
        if (!allowCustom(cycle)) {
            throw new RuleViolationException(List.of("Schemat oceny nie dopuszcza pytań własnych w ankiecie"));
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_QUESTION_LENGTH) {
            throw new RuleViolationException(List.of("Treść pytania: od 1 do " + MAX_QUESTION_LENGTH + " znaków"));
        }
        PreInterviewSurvey survey = editable(cycle);
        List<Question> questions = new ArrayList<>(questions(survey));
        if (questions.stream().filter(Question::custom).count() >= MAX_CUSTOM_QUESTIONS) {
            throw new RuleViolationException(List.of("Najwyżej " + MAX_CUSTOM_QUESTIONS + " pytań własnych"));
        }
        String id = "C" + UUID.randomUUID().toString().substring(0, 8);
        questions.add(new Question(id, trimmed, true, user.getId(), null));
        save(survey, questions);
        audit.log(user, cycle.getOfficeId(), "SURVEY_QUESTION_ADDED", AuditService.CYCLE, cycleId,
                workflow.auditPayload(user, cycle, Map.of("questionId", id)));
        return view(cycleId, user);
    }

    public SurveyView removeQuestion(Long cycleId, User user, String questionId) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        Role access = accessOf(cycle, user);
        if (access != Role.EVALUATOR && access != Role.HR) {
            throw forbidden("Pytania własne usuwa przełożony lub kadry");
        }
        PreInterviewSurvey survey = editable(cycle);
        List<Question> questions = new ArrayList<>(questions(survey));
        if (!questions.removeIf(q -> q.custom() && q.id().equals(questionId))) {
            throw notFound("Pytanie własne", questionId);
        }
        save(survey, questions);
        return view(cycleId, user);
    }

    /** Odpowiedzi zapisuje wyłącznie oceniany (szkic — przełożony ich nie widzi do wysłania). */
    public SurveyView saveAnswers(Long cycleId, User user, Map<String, String> answers) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        if (accessOf(cycle, user) != Role.EVALUATED) {
            throw forbidden("Ankietę wypełnia wyłącznie oceniany");
        }
        PreInterviewSurvey survey = editable(cycle);
        List<Question> updated = questions(survey).stream().map(q -> {
            String a = answers == null ? null : answers.get(q.id());
            if (a != null && a.length() > MAX_ANSWER_LENGTH) {
                throw new RuleViolationException(List.of("Odpowiedź: najwyżej " + MAX_ANSWER_LENGTH + " znaków"));
            }
            return a == null ? q : q.withAnswer(a.isBlank() ? null : a.trim());
        }).toList();
        save(survey, updated);
        return view(cycleId, user);
    }

    /** „Wyślij przełożonemu” — odpowiedzi stają się widoczne dla oceniającego, ankieta zostaje zablokowana. */
    public SurveyView send(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        if (accessOf(cycle, user) != Role.EVALUATED) {
            throw forbidden("Ankietę wysyła wyłącznie oceniany");
        }
        PreInterviewSurvey survey = editable(cycle);
        if (questions(survey).stream().allMatch(q -> q.answer() == null)) {
            throw new RuleViolationException(List.of("Uzupełnij co najmniej jedną odpowiedź przed wysłaniem"));
        }
        survey.setStatus(Status.SENT);
        survey.setSentAt(OffsetDateTime.now(clock));
        save(survey, questions(survey));
        audit.log(user, cycle.getOfficeId(), "PRE_INTERVIEW_SURVEY_SENT", AuditService.CYCLE, cycleId,
                Map.of("answered", questions(survey).stream().filter(q -> q.answer() != null).count()));
        return view(cycleId, user);
    }

    /** Rola w kontekście ankiety: oceniany, oceniający (lub zastępca), kadry urzędu; pozostali — 403. */
    private Role accessOf(EvaluationCycle cycle, User user) {
        if (user.getId().equals(cycle.getEmployeeId())) {
            return Role.EVALUATED;
        }
        if (workflow.canActAsEvaluator(cycle, user)) {
            return Role.EVALUATOR;
        }
        if (user.getRole() == Role.HR && user.getOfficeId().equals(cycle.getOfficeId())) {
            return Role.HR;
        }
        throw forbidden("Ankieta przygotowawcza jest dostępna dla ocenianego, przełożonego i kadr");
    }

    private PreInterviewSurvey editable(EvaluationCycle cycle) {
        if (!cycle.getStatus().isActive()) {
            throw conflict("Cykl jest zamknięty");
        }
        PreInterviewSurvey survey = surveys.findByCycleId(cycle.getId()).orElseGet(() -> fresh(cycle));
        if (survey.getStatus() == Status.SENT) {
            throw conflict("Ankieta została już wysłana przełożonemu");
        }
        return survey;
    }

    private PreInterviewSurvey fresh(EvaluationCycle cycle) {
        PreInterviewSurvey s = new PreInterviewSurvey();
        s.setCycleId(cycle.getId());
        List<Question> defaults = new ArrayList<>();
        List<String> texts = defaultQuestions(cycle);
        for (int i = 0; i < texts.size(); i++) {
            defaults.add(new Question("Q" + (i + 1), texts.get(i), false, null, null));
        }
        s.setQuestionsJson(json.valueToTree(defaults));
        return s;
    }

    private void save(PreInterviewSurvey survey, List<Question> questions) {
        survey.setQuestionsJson(json.valueToTree(questions));
        survey.setUpdatedAt(OffsetDateTime.now(clock));
        surveys.save(survey);
    }

    private List<Question> questions(PreInterviewSurvey survey) {
        return json.convertValue(survey.getQuestionsJson(), new TypeReference<>() {
        });
    }

    private JsonNode surveyConfig(EvaluationCycle cycle) {
        return schemas.findById(cycle.getSchemaVersionId())
                .map(s -> s.getConfigJson() == null ? null : s.getConfigJson().path("preInterviewSurvey"))
                .orElse(null);
    }

    /** Pytania własne w ankiecie domyślnie dozwolone; schemat może je wyłączyć ({@code allowCustom: false}). */
    private boolean allowCustom(EvaluationCycle cycle) {
        JsonNode cfg = surveyConfig(cycle);
        return cfg == null || cfg.isMissingNode() || cfg.path("allowCustom").asBoolean(true);
    }

    private List<String> defaultQuestions(EvaluationCycle cycle) {
        JsonNode cfg = surveyConfig(cycle);
        if (cfg != null && cfg.path("questions").isArray() && !cfg.path("questions").isEmpty()) {
            List<String> texts = new ArrayList<>();
            cfg.path("questions").forEach(q -> texts.add(q.asText()));
            return texts;
        }
        return DEFAULT_QUESTIONS;
    }
}
