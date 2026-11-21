package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.rules.*;
import pl.gov.kprm.oceny.repository.EmployeeNoteRepository;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.SchemaVersionRepository;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static pl.gov.kprm.oceny.service.Access.*;

/** Maszyna stanów cyklu oceny. Każda zmiana stanu trafia do audit_logs. */
@Service
@Transactional
@RequiredArgsConstructor
public class CycleWorkflowService {
    private static final Set<CycleStatus> NOTES_VISIBLE_TO_EMPLOYEE =
            EnumSet.of(CycleStatus.AWAITING_ACK, CycleStatus.COMPLETED, CycleStatus.APPEALED);

    private final EvaluationCycleRepository cycles;
    private final EmployeeNoteRepository notes;
    private final SchemaVersionRepository schemas;
    private final UserRepository users;
    private final EvaluationRuleEngine engine;
    private final DeadlineService deadlines;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EvaluationCycle> visibleFor(User user) {
        return switch (user.getRole()) {
            case KPRM_ADMIN -> cycles.findAllByOrderByIdAsc();
            case HR, DG -> cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId());
            case EVALUATOR, EVALUATED -> cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(user.getId(), user.getId());
        };
    }

    @Transactional(readOnly = true)
    public EvaluationCycle get(Long id, User user) {
        EvaluationCycle cycle = load(id);
        boolean officeStaff = (user.getRole() == Role.HR || user.getRole() == Role.DG)
                && user.getOfficeId().equals(cycle.getOfficeId());
        if (!isParty(cycle, user) && !officeStaff && user.getRole() != Role.KPRM_ADMIN) {
            throw forbidden("Brak dostępu do cyklu " + id);
        }
        return cycle;
    }

    public EvaluationCycle setCriteria(Long id, User user, List<Criterion> requested) {
        EvaluationCycle cycle = asEvaluator(id, user);
        requireStatus(cycle, CycleStatus.DRAFT_CRITERIA);
        SchemaVersion schema = schemas.findById(cycle.getSchemaVersionId())
                .orElseThrow(() -> notFound("Schemat", cycle.getSchemaVersionId()));
        List<Criterion> mandatory = mandatoryCriteria(schema, cycle.getSheetType());
        List<Criterion> additional = resolveAdditional(schema, requested);
        engine.validateAdditionalCriteria(mandatory, additional);

        List<Criterion> all = new ArrayList<>(mandatory);
        all.addAll(additional);
        cycle.setCriteriaJson(json.valueToTree(all));
        audit.log(user, cycle.getOfficeId(), "CRITERIA_SET", AuditService.CYCLE, cycle.getId(),
                Map.of("criteria", all.stream().map(Criterion::code).toList()));
        return cycle;
    }

    public EvaluationCycle confirmCriteria(Long id, User user) {
        EvaluationCycle cycle = asEmployee(id, user);
        if (criteriaOf(cycle).isEmpty()) {
            throw conflict("Przełożony nie ustalił jeszcze kryteriów");
        }
        transition(cycle, user, CycleStatus.OBSERVATION, "CRITERIA_CONFIRMED", Map.of());
        return cycle;
    }

    public EmployeeNote addNote(Long id, User user, String content, boolean sharedAfterEval) {
        EvaluationCycle cycle = asEvaluator(id, user);
        if (!cycle.getStatus().isActive()) {
            throw conflict("Nie można dodawać notatek do zakończonego cyklu");
        }
        EmployeeNote note = new EmployeeNote();
        note.setCycleId(cycle.getId());
        note.setAuthorId(user.getId());
        note.setContent(content);
        note.setSharedAfterEval(sharedAfterEval);
        EmployeeNote saved = notes.saveAndFlush(note);
        audit.log(user, cycle.getOfficeId(), "NOTE_ADDED", AuditService.CYCLE, cycle.getId(),
                Map.of("noteId", saved.getId(), "isSharedAfterEval", sharedAfterEval));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeNote> notes(Long id, User user) {
        EvaluationCycle cycle = get(id, user);
        List<EmployeeNote> all = notes.findByCycleIdOrderByCreatedAtAsc(cycle.getId());
        if (user.getId().equals(cycle.getEvaluatorId())) {
            return all;
        }
        if (user.getId().equals(cycle.getEmployeeId())) {
            boolean evaluated = NOTES_VISIBLE_TO_EMPLOYEE.contains(cycle.getStatus());
            return all.stream()
                    .filter(n -> n.getAuthorId().equals(user.getId()) || (evaluated && n.isSharedAfterEval()))
                    .toList();
        }
        throw forbidden("Notatki widzi wyłącznie oceniający i oceniany");
    }

    public EvaluationResult evaluate(Long id, User user, Map<String, Integer> scores, String justification,
                                     String iprzNotes, GradeRequest gradeRequest, boolean submit) {
        EvaluationCycle cycle = asEvaluator(id, user);
        cycle.setScoresJson(json.valueToTree(scores));
        cycle.setJustification(justification);
        cycle.setIprzNotes(iprzNotes);
        cycle.setGradeRequestJson(gradeRequest == null ? null : json.valueToTree(gradeRequest));
        if (!submit) {
            engine.validateScale(scores);
            transition(cycle, user, CycleStatus.EVALUATION_DRAFT, "EVALUATION_DRAFT_SAVED", Map.of());
            return null;
        }
        EvaluationResult result = assessOrThrow(cycle, scores, justification, iprzNotes, gradeRequest);
        applyResult(cycle, result);
        transition(cycle, user, CycleStatus.AWAITING_ACK, "EVALUATION_SUBMITTED", resultPayload(result));
        return result;
    }

    public EvaluationCycle acknowledge(Long id, User user) {
        EvaluationCycle cycle = asEmployee(id, user);
        LocalDate today = LocalDate.now(clock);
        cycle.setAcknowledgedAt(today);
        cycle.setAppealDeadline(deadlines.appealDeadline(today));
        transition(cycle, user, CycleStatus.COMPLETED, "EVALUATION_ACKNOWLEDGED",
                Map.of("appealDeadline", cycle.getAppealDeadline()));
        return cycle;
    }

    /** Fikcja zapoznania: odmowa podpisu jest odnotowywana, a termin na sprzeciw biegnie od wskazanej daty. */
    public EvaluationCycle registerRefusal(Long id, User user, String reason, LocalDate date) {
        EvaluationCycle cycle = load(id);
        boolean hr = user.getRole() == Role.HR && user.getOfficeId().equals(cycle.getOfficeId());
        if (!user.getId().equals(cycle.getEvaluatorId()) && !hr) {
            throw forbidden("Odmowę zapoznania rejestruje oceniający lub kadry");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw conflict("Data odmowy nie może być z przyszłości");
        }
        cycle.setRefusalReason(reason);
        cycle.setRefusalDate(date);
        cycle.setAcknowledgedAt(date);
        cycle.setAppealDeadline(deadlines.appealDeadline(date));
        transition(cycle, user, CycleStatus.COMPLETED, "ACKNOWLEDGEMENT_REFUSED",
                Map.of("reason", reason, "date", date, "appealDeadline", cycle.getAppealDeadline()));
        return cycle;
    }

    public EvaluationCycle appeal(Long id, User user, String content) {
        EvaluationCycle cycle = asEmployee(id, user);
        if (cycle.getAppealDeadline() != null && LocalDate.now(clock).isAfter(cycle.getAppealDeadline())) {
            throw conflict("Termin na złożenie sprzeciwu upłynął " + cycle.getAppealDeadline());
        }
        cycle.setAppealText(content);
        cycle.setAppealedAt(OffsetDateTime.now(clock));
        transition(cycle, user, CycleStatus.APPEALED, "APPEAL_FILED", Map.of());
        return cycle;
    }

    public EvaluationCycle decideAppeal(Long id, User user, DgDecision decision, String comment,
                                        Map<String, Integer> scores, String justification, String iprzNotes) {
        EvaluationCycle cycle = load(id);
        requireRole(user, Role.DG);
        requireSameOffice(user, cycle.getOfficeId());
        requireStatus(cycle, CycleStatus.APPEALED);
        cycle.setDgDecision(decision);
        cycle.setDgComment(comment);
        cycle.setDgDecidedAt(OffsetDateTime.now(clock));

        if (decision == DgDecision.REDO) {
            transition(cycle, user, CycleStatus.EVALUATION_DRAFT, "APPEAL_DECIDED",
                    payload("decision", decision, "comment", comment));
            return cycle;
        }
        if (scores == null || scores.isEmpty()) {
            throw new RuleViolationException(List.of("Zmiana oceny wymaga podania nowych ocen cząstkowych"));
        }
        String newJustification = justification != null ? justification : cycle.getJustification();
        String newIprz = iprzNotes != null ? iprzNotes : cycle.getIprzNotes();
        GradeRequest grade = cycle.getGradeRequestJson() == null ? null
                : json.convertValue(cycle.getGradeRequestJson(), GradeRequest.class);
        EvaluationResult result = assessOrThrow(cycle, scores, newJustification, newIprz, grade);
        cycle.setScoresJson(json.valueToTree(scores));
        cycle.setJustification(newJustification);
        cycle.setIprzNotes(newIprz);
        applyResult(cycle, result);
        Map<String, Object> p = resultPayload(result);
        p.put("decision", decision);
        p.put("comment", comment);
        transition(cycle, user, CycleStatus.COMPLETED, "APPEAL_DECIDED", p);
        return cycle;
    }

    private EvaluationResult assessOrThrow(EvaluationCycle cycle, Map<String, Integer> scores, String justification,
                                           String iprzNotes, GradeRequest grade) {
        User employee = users.findById(cycle.getEmployeeId())
                .orElseThrow(() -> notFound("Użytkownik", cycle.getEmployeeId()));
        EvaluationResult result = engine.assess(new EvaluationInput(criteriaOf(cycle), scores, justification,
                iprzNotes, grade, employee.isCivilServant()));
        if (!result.isValid()) {
            throw new RuleViolationException(result.violations(), result);
        }
        return result;
    }

    private static void applyResult(EvaluationCycle cycle, EvaluationResult result) {
        cycle.setAverageScore(result.average().setScale(2, RoundingMode.HALF_UP));
        cycle.setPerformanceLevel(result.level());
        cycle.setFinalResult(result.outcome());
        cycle.setResultExplanation(result.explanation());
    }

    private static Map<String, Object> resultPayload(EvaluationResult r) {
        return payload("average", r.averageDisplay(), "level", r.level(), "outcome", r.outcome());
    }

    private void transition(EvaluationCycle cycle, User actor, CycleStatus target, String action,
                            Map<String, ?> extra) {
        CycleStatus from = cycle.getStatus();
        if (!from.canTransitionTo(target)) {
            throw conflict("Niedozwolone przejście " + from + " -> " + target);
        }
        cycle.setStatus(target);
        Map<String, Object> p = new LinkedHashMap<>(extra);
        p.put("from", from);
        p.put("to", target);
        audit.log(actor, cycle.getOfficeId(), action, AuditService.CYCLE, cycle.getId(), p);
    }

    private List<Criterion> criteriaOf(EvaluationCycle cycle) {
        if (cycle.getCriteriaJson() == null) {
            return List.of();
        }
        return json.convertValue(cycle.getCriteriaJson(), new TypeReference<>() {
        });
    }

    private static List<Criterion> mandatoryCriteria(SchemaVersion schema, SheetType sheet) {
        List<Criterion> out = new ArrayList<>();
        for (JsonNode n : schema.getConfigJson().path("criteria")) {
            if (n.path("mandatory").asBoolean() && appliesTo(n, sheet)) {
                out.add(new Criterion(n.path("code").asText(), n.path("name").asText(), true));
            }
        }
        return out;
    }

    /** Kryterium z katalogu schematu (po kodzie) albo własne (tylko nazwa) — kod nadawany jako D1..Dn. */
    private static List<Criterion> resolveAdditional(SchemaVersion schema, List<Criterion> requested) {
        List<Criterion> out = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        int custom = 0;
        for (Criterion c : requested) {
            if (c.code() != null && !c.code().isBlank()) {
                Optional<JsonNode> entry = catalogEntry(schema, c.code());
                if (entry.isEmpty()) {
                    violations.add("Kryterium " + c.code() + " nie istnieje w schemacie " + schema.getVersionCode());
                } else {
                    out.add(new Criterion(entry.get().path("code").asText(), entry.get().path("name").asText(),
                            entry.get().path("mandatory").asBoolean()));
                }
            } else if (c.name() != null && !c.name().isBlank()) {
                out.add(new Criterion("D" + (++custom), c.name().trim(), false));
            } else {
                violations.add("Kryterium dodatkowe wymaga kodu lub nazwy");
            }
        }
        if (!violations.isEmpty()) {
            throw new RuleViolationException(violations);
        }
        return out;
    }

    private static Optional<JsonNode> catalogEntry(SchemaVersion schema, String code) {
        for (JsonNode n : schema.getConfigJson().path("criteria")) {
            if (n.path("code").asText().equalsIgnoreCase(code.trim())) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    private static boolean appliesTo(JsonNode criterion, SheetType sheet) {
        JsonNode sheets = criterion.path("sheets");
        if (!sheets.isArray() || sheets.isEmpty()) {
            return true;
        }
        for (JsonNode s : sheets) {
            if (s.asText().equals(sheet.name())) return true;
        }
        return false;
    }

    private EvaluationCycle load(Long id) {
        return cycles.findById(id).orElseThrow(() -> notFound("Cykl", id));
    }

    private EvaluationCycle asEvaluator(Long id, User user) {
        EvaluationCycle cycle = load(id);
        if (!user.getId().equals(cycle.getEvaluatorId())) {
            throw forbidden("Operację wykonuje wyłącznie oceniający");
        }
        return cycle;
    }

    private EvaluationCycle asEmployee(Long id, User user) {
        EvaluationCycle cycle = load(id);
        if (!user.getId().equals(cycle.getEmployeeId())) {
            throw forbidden("Operację wykonuje wyłącznie oceniany");
        }
        return cycle;
    }

    private static boolean isParty(EvaluationCycle cycle, User user) {
        return user.getId().equals(cycle.getEmployeeId()) || user.getId().equals(cycle.getEvaluatorId());
    }

    private static void requireStatus(EvaluationCycle cycle, CycleStatus expected) {
        if (cycle.getStatus() != expected) {
            throw conflict("Wymagany status " + expected + ", obecny " + cycle.getStatus());
        }
    }

    private static Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
