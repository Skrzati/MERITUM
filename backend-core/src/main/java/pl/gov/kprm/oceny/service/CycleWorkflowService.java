package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.*;
import pl.gov.kprm.oceny.rules.*;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    private final OfficeRepository offices;
    private final AbsenceRepository absences;
    private final ScopeChangeRepository scopeChanges;
    private final EvaluationHistoryService history;
    private final EvaluationRuleEngine engine;
    private final DeadlineService deadlines;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EvaluationCycle> visibleFor(User user, Optional<PageRequest> page) {
        if (page.isEmpty()) {
            return visibleFor(user);
        }
        Pageable p = page.get();
        return switch (user.getRole()) {
            case KPRM_ADMIN -> cycles.findAllByOrderByIdAsc(p);
            case HR, DG -> cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId(), p);
            case EVALUATOR, EVALUATED -> cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(user.getId(), user.getId(), p);
        };
    }

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

    /**
     * Nowy cykl zawsze przypina najnowszą obowiązującą wersję schematu, a termin wylicza z jej okresu oceny
     * (lub 6 miesięcy przy ponownej ocenie po negatywnej — art. 81 ust. 9).
     */
    public EvaluationCycle create(User user, Long employeeId, SheetType sheetType, LocalDate startDate, boolean reevaluation) {
        User employee = users.findById(employeeId).orElseThrow(() -> notFound("Użytkownik", employeeId));
        requireSameOffice(user, employee.getOfficeId());
        boolean hr = user.getRole() == Role.HR;
        boolean supervisor = user.getId().equals(employee.getSupervisorId());
        if (!hr && !supervisor) {
            throw forbidden("Cykl tworzy kadra lub bezpośredni przełożony ocenianego");
        }
        if (employee.getSupervisorId() == null) {
            throw conflict("Pracownik nie ma przypisanego bezpośredniego przełożonego");
        }
        if (cycles.findByEmployeeIdOrderByIdAsc(employeeId).stream().anyMatch(c -> c.getStatus().isActive())) {
            throw conflict("Pracownik ma już aktywny cykl oceny");
        }
        LocalDate start = startDate != null ? startDate : LocalDate.now(clock);
        SchemaVersion schema = schemas.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate.now(clock))
                .orElseThrow(() -> conflict("Brak obowiązującej wersji schematu oceny"));
        List<AbsencePeriod> periods = absences.findByUserIdOrderByStartDateAsc(employeeId).stream()
                .map(a -> new AbsencePeriod(a.getStartDate(), a.getEndDate())).toList();

        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setOfficeId(employee.getOfficeId());
        cycle.setEmployeeId(employeeId);
        cycle.setEvaluatorId(employee.getSupervisorId());
        cycle.setSchemaVersionId(schema.getId());
        cycle.setSheetType(sheetType != null ? sheetType : defaultSheet(employee));
        cycle.setStatus(CycleStatus.DRAFT_CRITERIA);
        cycle.setStartDate(start);
        cycle.setReevaluation(reevaluation);
        cycle.setDueDate(reevaluation ? deadlines.reevaluationDue(start)
                : deadlines.periodicEvaluationDue(start, schema.getPeriodMonths(), periods));
        cycle.setPlace(officeCity(employee.getOfficeId()));
        EvaluationCycle saved = cycles.saveAndFlush(cycle);
        audit.log(user, saved.getOfficeId(), "CYCLE_CREATED", AuditService.CYCLE, saved.getId(),
                payload("employeeId", employeeId, "schemaVersion", schema.getVersionCode(),
                        "periodMonths", schema.getPeriodMonths(), "dueDate", saved.getDueDate(),
                        "reevaluation", reevaluation));
        return saved;
    }

    /** Kolejny cykl po zakończonej ocenie (HR lub oceniający) — na najnowszej wersji schematu. */
    public EvaluationCycle next(Long id, User user) {
        EvaluationCycle previous = load(id);
        requireStatus(previous, CycleStatus.COMPLETED);
        boolean hr = user.getRole() == Role.HR && user.getOfficeId().equals(previous.getOfficeId());
        if (!hr && !user.getId().equals(previous.getEvaluatorId())) {
            throw forbidden("Kolejny cykl uruchamia kadra lub oceniający");
        }
        NextCycleRules.Plan plan = NextCycleRules.plan(previous.getFinalResult(), previous.getEvaluatedOn(),
                previous.getAcknowledgedAt(), LocalDate.now(clock));
        EvaluationCycle next = create(user, previous.getEmployeeId(), previous.getSheetType(), plan.startDate(),
                plan.reevaluation());
        audit.log(user, next.getOfficeId(), "NEXT_CYCLE_CREATED", AuditService.CYCLE, next.getId(),
                payload("previousCycleId", previous.getId(), "previousOutcome", previous.getFinalResult(),
                        "reevaluation", plan.reevaluation(), "dueDate", next.getDueDate()));
        return next;
    }

    public EvaluationCycle setCriteria(Long id, User user, List<Criterion> requested, LocalDate interviewDate) {
        EvaluationCycle cycle = asEvaluator(id, user);
        requireStatus(cycle, CycleStatus.DRAFT_CRITERIA);
        SchemaVersion schema = schemas.findById(cycle.getSchemaVersionId())
                .orElseThrow(() -> notFound("Schemat", cycle.getSchemaVersionId()));
        List<Criterion> all = CriteriaCatalog.from(schema.getConfigJson()).select(cycle.getSheetType(), requested);
        cycle.setCriteriaJson(json.valueToTree(all));
        cycle.setCriteriaInterviewDate(interviewDate != null ? interviewDate : LocalDate.now(clock));
        audit.log(user, cycle.getOfficeId(), "CRITERIA_SET", AuditService.CYCLE, cycle.getId(),
                Map.of("criteria", all.stream().map(Criterion::code).toList(),
                        "interviewDate", cycle.getCriteriaInterviewDate()));
        return cycle;
    }

    public EvaluationCycle confirmCriteria(Long id, User user) {
        EvaluationCycle cycle = asEmployee(id, user);
        if (criteriaOf(cycle).isEmpty()) {
            throw conflict("Przełożony nie ustalił jeszcze kryteriów");
        }
        cycle.setCriteriaConfirmedOn(LocalDate.now(clock));
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
                                     String iprzNotes, GradeRequest gradeRequest, LocalDate interviewDate,
                                     boolean submit, String delayReason, SignatureMethod signatureMethod) {
        EvaluationCycle cycle = asEvaluator(id, user);
        cycle.setScoresJson(json.valueToTree(scores));
        cycle.setJustification(justification);
        cycle.setIprzNotes(iprzNotes);
        cycle.setGradeRequestJson(gradeRequest == null ? null : json.valueToTree(gradeRequest));
        if (interviewDate != null) {
            cycle.setEvaluationInterviewDate(interviewDate);
        }
        if (delayReason != null && !delayReason.isBlank()) {
            cycle.setDelayReason(delayReason.trim());
        }
        if (signatureMethod != null) {
            cycle.setSignatureMethod(signatureMethod);
        }
        if (!submit) {
            engine.validateScale(scores);
            transition(cycle, user, CycleStatus.EVALUATION_DRAFT, "EVALUATION_DRAFT_SAVED", Map.of());
            return null;
        }
        EvaluationResult result = assessOrThrow(cycle, scores, justification, iprzNotes, gradeRequest);
        LocalDate today = LocalDate.now(clock);
        long overdueDays = OverdueRules.overdueDays(cycle.getDueDate(), today, true);
        if (overdueDays > 0) {
            // ustalenie z KPRM: termin nie blokuje sporządzenia oceny — opóźnienie jest rejestrowane
            audit.log(user, cycle.getOfficeId(), "EVALUATION_OVERDUE", AuditService.CYCLE, cycle.getId(),
                    payload("dueDate", cycle.getDueDate(), "overdueDays", overdueDays,
                            "delayReason", cycle.getDelayReason()));
        }
        cycle.setEvaluatedOn(today);
        if (cycle.getEvaluationInterviewDate() == null) {
            cycle.setEvaluationInterviewDate(today);
        }
        applyResult(cycle, result);
        transition(cycle, user, CycleStatus.AWAITING_ACK, "EVALUATION_SUBMITTED", resultPayload(result));
        return result;
    }

    public EvaluationCycle acknowledge(Long id, User user, SignatureMethod signatureMethod) {
        EvaluationCycle cycle = asEmployee(id, user);
        LocalDate today = LocalDate.now(clock);
        cycle.setAcknowledgedAt(today);
        cycle.setAppealDeadline(deadlines.appealDeadline(today));
        cycle.setSignatureMethod(signatureMethod != null ? signatureMethod : SignatureMethod.SYSTEM_DEMO);
        transition(cycle, user, CycleStatus.COMPLETED, "EVALUATION_ACKNOWLEDGED",
                Map.of("appealDeadline", cycle.getAppealDeadline(), "signatureMethod", cycle.getSignatureMethod()));
        history.recordCompleted(cycle);
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
        history.recordCompleted(cycle);
        return cycle;
    }

    public EvaluationCycle appeal(Long id, User user, String content) {
        EvaluationCycle cycle = asEmployee(id, user);
        LocalDate today = LocalDate.now(clock);
        if (cycle.getAppealDeadline() != null && today.isAfter(cycle.getAppealDeadline())) {
            throw conflict("Termin na złożenie sprzeciwu upłynął " + cycle.getAppealDeadline());
        }
        cycle.setAppealText(content);
        cycle.setAppealedAt(OffsetDateTime.now(clock));
        cycle.setAppealDecisionDeadline(deadlines.appealDecisionDeadline(today));
        transition(cycle, user, CycleStatus.APPEALED, "APPEAL_FILED",
                Map.of("decisionDeadline", cycle.getAppealDecisionDeadline()));
        return cycle;
    }

    /** Art. 83 ust. 4: w razie uwzględnienia sprzeciwu ocenę zmienia się albo sporządza po raz drugi. */
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
            history.removeForCycle(cycle.getId());
            cycle.setEvaluatedOn(null);
            cycle.setAcknowledgedAt(null);
            cycle.setAppealDeadline(null);
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
        history.recordCompleted(cycle);
        return cycle;
    }

    /** Fakty z bazy dla HR Partnera; {@code daysSinceLastNote = null}, gdy w cyklu nie ma notatek. */
    public record HrFacts(long absenceDays, long scopeChangesCount, long overloadNotesCount, long totalNotesCount,
                          Long daysSinceLastNote, long overdueDays) {
    }

    @Transactional(readOnly = true)
    public HrFacts hrFacts(Long id, User user) {
        EvaluationCycle cycle = get(id, user);
        // D4: oceniany nie widzi metadanych roboczych notatek przełożonego (liczba, data ostatniej)
        if (user.getRole() == Role.EVALUATED || user.getId().equals(cycle.getEmployeeId())) {
            throw forbidden("Fakty dla HR Partnera są dostępne dla przełożonego i kadr, nie dla ocenianego");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate periodEnd = cycle.getDueDate().isBefore(today) ? cycle.getDueDate() : today;
        long absenceDays = absences.findByUserIdOrderByStartDateAsc(cycle.getEmployeeId()).stream()
                .mapToLong(a -> HrFactsRules.absenceDaysWithin(a.getStartDate(), a.getEndDate(),
                        cycle.getStartDate(), periodEnd))
                .sum();
        List<EmployeeNote> cycleNotes = notes.findByCycleIdOrderByCreatedAtAsc(id);
        Long daysSinceLastNote = cycleNotes.isEmpty() ? null : ChronoUnit.DAYS.between(
                cycleNotes.get(cycleNotes.size() - 1).getCreatedAt().atZoneSameInstant(clock.getZone()).toLocalDate(),
                today);
        return new HrFacts(absenceDays,
                scopeChanges.findByCycleIdOrderByChangeDateAsc(id).size(),
                cycleNotes.stream().filter(n -> HrFactsRules.signalsOverload(n.getContent())).count(),
                cycleNotes.size(),
                daysSinceLastNote,
                OverdueRules.overdueDays(cycle.getDueDate(), today, OverdueService.awaitingEvaluation(cycle)));
    }

    /** Termin na ustalenie kryteriów (§ 7): 30 dni od rozpoczęcia cyklu albo 7 dni po ocenie negatywnej. */
    @Transactional(readOnly = true)
    public LocalDate criteriaDeadline(EvaluationCycle cycle) {
        return deadlines.initialInterviewDeadline(cycle.getStartDate(), cycle.isReevaluation());
    }

    private EvaluationResult assessOrThrow(EvaluationCycle cycle, Map<String, Integer> scores, String justification,
                                           String iprzNotes, GradeRequest grade) {
        User employee = users.findById(cycle.getEmployeeId())
                .orElseThrow(() -> notFound("Użytkownik", cycle.getEmployeeId()));
        EvaluationResult result = engine.assess(new EvaluationInput(criteriaOf(cycle), scores, justification,
                iprzNotes, grade, employee.isCivilServant(), employee.getCivilServiceGrade()));
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

    void transition(EvaluationCycle cycle, User actor, CycleStatus target, String action, Map<String, ?> extra) {
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

    List<Criterion> criteriaOf(EvaluationCycle cycle) {
        if (cycle.getCriteriaJson() == null) {
            return List.of();
        }
        return json.convertValue(cycle.getCriteriaJson(), new TypeReference<>() {
        });
    }

    EvaluationCycle load(Long id) {
        return cycles.findById(id).orElseThrow(() -> notFound("Cykl", id));
    }

    EvaluationCycle asEvaluator(Long id, User user) {
        EvaluationCycle cycle = load(id);
        if (!user.getId().equals(cycle.getEvaluatorId())) {
            throw forbidden("Operację wykonuje wyłącznie oceniający");
        }
        return cycle;
    }

    EvaluationCycle asEmployee(Long id, User user) {
        EvaluationCycle cycle = load(id);
        if (!user.getId().equals(cycle.getEmployeeId())) {
            throw forbidden("Operację wykonuje wyłącznie oceniany");
        }
        return cycle;
    }

    private String officeCity(Long officeId) {
        return offices.findById(officeId).map(Office::getCity).orElse(null);
    }

    private static SheetType defaultSheet(User employee) {
        return employee.getRole() == Role.EVALUATOR || employee.getRole() == Role.DG ? SheetType.B : SheetType.C;
    }

    private static boolean isParty(EvaluationCycle cycle, User user) {
        return user.getId().equals(cycle.getEmployeeId()) || user.getId().equals(cycle.getEvaluatorId());
    }

    static void requireStatus(EvaluationCycle cycle, CycleStatus expected) {
        if (cycle.getStatus() != expected) {
            throw conflict("Wymagany status " + expected + ", obecny " + cycle.getStatus());
        }
    }

    static Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
