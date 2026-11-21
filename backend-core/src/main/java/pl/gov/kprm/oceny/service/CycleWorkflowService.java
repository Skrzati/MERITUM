package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
    static final int MAX_BULK_CYCLES = 500;
    private static final int REEVALUATION_MONTHS = 6;
    private static final Set<CycleStatus> NOTES_VISIBLE_TO_EMPLOYEE =
            EnumSet.of(CycleStatus.AWAITING_ACK, CycleStatus.COMPLETED, CycleStatus.APPEALED);

    private final EvaluationCycleRepository cycles;
    private final EmployeeNoteRepository notes;
    private final SchemaVersionRepository schemas;
    private final UserRepository users;
    private final OfficeRepository offices;
    private final AbsenceRepository absences;
    private final ScopeChangeRepository scopeChanges;
    private final SubstitutionService substitutions;
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
        if (!substitutions.substitutedBy(user.getId()).isEmpty()) {
            List<EvaluationCycle> all = visibleFor(user);
            int from = (int) Math.min(p.getOffset(), all.size());
            return all.subList(from, Math.min(from + p.getPageSize(), all.size()));
        }
        return switch (user.getRole()) {
            case KPRM_ADMIN -> cycles.findAllByOrderByIdAsc(p);
            case HR, DG -> cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId(), p);
            case EVALUATOR, EVALUATED -> cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(user.getId(), user.getId(), p);
        };
    }

    @Transactional(readOnly = true)
    public List<EvaluationCycle> visibleFor(User user) {
        List<EvaluationCycle> own = switch (user.getRole()) {
            case KPRM_ADMIN -> cycles.findAllByOrderByIdAsc();
            case HR, DG -> cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId());
            case EVALUATOR, EVALUATED -> cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(user.getId(), user.getId());
        };
        Set<Long> substituted = substitutions.substitutedBy(user.getId());
        if (substituted.isEmpty()) {
            return own;
        }
        // zastępca widzi aktywne cykle zastępowanych oceniających
        Map<Long, EvaluationCycle> merged = new TreeMap<>();
        own.forEach(c -> merged.put(c.getId(), c));
        cycles.findByEvaluatorIdInOrderByIdAsc(substituted).stream()
                .filter(c -> c.getStatus().isActive())
                .forEach(c -> merged.putIfAbsent(c.getId(), c));
        return new ArrayList<>(merged.values());
    }

    /** Oceniający albo jego aktywny zastępca (tylko dla aktywnych cykli). */
    public boolean canActAsEvaluator(EvaluationCycle cycle, User user) {
        return user.getId().equals(cycle.getEvaluatorId()) || isDeputy(cycle, user);
    }

    private boolean isDeputy(EvaluationCycle cycle, User user) {
        return cycle.getStatus().isActive() && !user.getId().equals(cycle.getEmployeeId())
                && substitutions.isActiveDeputy(user.getId(), cycle.getEvaluatorId());
    }

    /** Payload audytu z adnotacją zastępstwa, gdy akcję wykonuje zastępca oceniającego. */
    public Map<String, Object> auditPayload(User actor, EvaluationCycle cycle, Map<String, ?> payload) {
        Map<String, Object> p = new LinkedHashMap<>(payload);
        if (!actor.getId().equals(cycle.getEvaluatorId()) && !actor.getId().equals(cycle.getEmployeeId())
                && isDeputy(cycle, actor)) {
            p.put("substituteFor", cycle.getEvaluatorId());
        }
        return p;
    }

    @Transactional(readOnly = true)
    public EvaluationCycle get(Long id, User user) {
        EvaluationCycle cycle = load(id);
        boolean officeStaff = (user.getRole() == Role.HR || user.getRole() == Role.DG)
                && user.getOfficeId().equals(cycle.getOfficeId());
        if (!isParty(cycle, user) && !officeStaff && user.getRole() != Role.KPRM_ADMIN && !isDeputy(cycle, user)) {
            throw forbidden("Brak dostępu do cyklu " + id);
        }
        return cycle;
    }

    /**
     * Nowy cykl zawsze przypina najnowszą obowiązującą wersję schematu, a termin wylicza z jej okresu oceny
     * (lub 6 miesięcy przy ponownej ocenie po negatywnej — art. 81 ust. 9).
     */
    public EvaluationCycle create(User user, Long employeeId, SheetType sheetType, LocalDate startDate, boolean reevaluation) {
        return create(user, employeeId, sheetType, startDate, reevaluation, null, null);
    }

    /**
     * @param evaluatorId oceniający inny niż bezpośredni przełożony (np. przy zmianie podległości) — opcjonalnie
     * @param place       miejscowość na arkuszu — domyślnie miasto urzędu
     */
    public EvaluationCycle create(User user, Long employeeId, SheetType sheetType, LocalDate startDate, boolean reevaluation,
                                  Long evaluatorId, String place) {
        User employee = users.findById(employeeId).orElseThrow(() -> notFound("Użytkownik", employeeId));
        requireSameOffice(user, employee.getOfficeId());
        boolean officeStaff = user.getRole() == Role.HR || user.getRole() == Role.DG;
        boolean supervisor = user.getId().equals(employee.getSupervisorId());
        if (!officeStaff && !supervisor) {
            throw forbidden("Cykl tworzy kadra, kierownictwo urzędu lub bezpośredni przełożony ocenianego");
        }
        if (!employee.isActive()) {
            throw conflict("Konto pracownika jest nieaktywne (zanonimizowane)");
        }
        Long evaluator = evaluatorId != null ? evaluatorId : employee.getSupervisorId();
        if (evaluator == null) {
            throw conflict("Pracownik nie ma przypisanego bezpośredniego przełożonego");
        }
        if (evaluatorId != null) {
            requireEvaluatorFor(employee, evaluatorId);
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
        cycle.setEvaluatorId(evaluator);
        cycle.setSchemaVersionId(schema.getId());
        cycle.setSheetType(sheetType != null ? sheetType : defaultSheet(employee));
        cycle.setStatus(CycleStatus.DRAFT_CRITERIA);
        cycle.setStartDate(start);
        cycle.setReevaluation(reevaluation);
        cycle.setDueDate(reevaluation ? deadlines.reevaluationDue(start)
                : deadlines.periodicEvaluationDue(start, schema.getPeriodMonths(), periods));
        cycle.setPlace(place != null && !place.isBlank() ? place.trim() : officeCity(employee.getOfficeId()));
        EvaluationCycle saved = cycles.saveAndFlush(cycle);
        audit.log(user, saved.getOfficeId(), "CYCLE_CREATED", AuditService.CYCLE, saved.getId(),
                payload("employeeId", employeeId, "schemaVersion", schema.getVersionCode(),
                        "periodMonths", schema.getPeriodMonths(), "dueDate", saved.getDueDate(),
                        "reevaluation", reevaluation, "evaluatorId", evaluator, "sheetType", saved.getSheetType()));
        return saved;
    }

    public record BulkSkip(Long employeeId, String reason) {
    }

    public record BulkResult(List<EvaluationCycle> created, List<BulkSkip> skipped) {
    }

    /** Masowe otwarcie cykli (kadry / kierownictwo): lista pracowników albo cała komórka; pomija osoby z aktywnym cyklem. */
    public BulkResult createBulk(User user, List<Long> employeeIds, String department, LocalDate startDate) {
        requireRole(user, Role.HR, Role.DG);
        List<Long> ids;
        if (employeeIds != null && !employeeIds.isEmpty()) {
            ids = employeeIds.stream().distinct().toList();
        } else if (department != null && !department.isBlank()) {
            ids = users.findByOfficeIdAndDepartmentIgnoreCaseAndActiveTrue(user.getOfficeId(), department.trim()).stream()
                    .map(User::getId).toList();
        } else {
            throw new RuleViolationException(List.of("Wskaż pracowników albo komórkę organizacyjną"));
        }
        if (ids.size() > MAX_BULK_CYCLES) {
            throw new RuleViolationException(List.of("Jednorazowo można otworzyć najwyżej " + MAX_BULK_CYCLES + " cykli"));
        }
        List<EvaluationCycle> created = new ArrayList<>();
        List<BulkSkip> skipped = new ArrayList<>();
        for (Long id : ids) {
            try {
                created.add(create(user, id, null, startDate, false, null, null));
            } catch (ResponseStatusException e) {
                skipped.add(new BulkSkip(id, e.getReason()));
            }
        }
        audit.log(user, user.getOfficeId(), "CYCLES_BULK_CREATED", AuditService.CYCLE, null,
                payload("department", department, "created", created.stream().map(EvaluationCycle::getId).toList(),
                        "skipped", skipped.size()));
        return new BulkResult(created, skipped);
    }

    public record DuePreview(LocalDate startDate, LocalDate dueDate, LocalDate criteriaDeadline, int periodMonths,
                             String schemaVersion, SheetType suggestedSheet, Long defaultEvaluatorId, String defaultPlace) {
    }

    /** Podgląd terminu przed utworzeniem cyklu — te same reguły co {@link #create}. */
    @Transactional(readOnly = true)
    public DuePreview previewDue(User user, Long employeeId, LocalDate startDate, boolean reevaluation) {
        User employee = users.findById(employeeId).orElseThrow(() -> notFound("Użytkownik", employeeId));
        requireSameOffice(user, employee.getOfficeId());
        LocalDate start = startDate != null ? startDate : LocalDate.now(clock);
        SchemaVersion schema = schemas.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate.now(clock))
                .orElseThrow(() -> conflict("Brak obowiązującej wersji schematu oceny"));
        List<AbsencePeriod> periods = absences.findByUserIdOrderByStartDateAsc(employeeId).stream()
                .map(a -> new AbsencePeriod(a.getStartDate(), a.getEndDate())).toList();
        LocalDate due = reevaluation ? deadlines.reevaluationDue(start)
                : deadlines.periodicEvaluationDue(start, schema.getPeriodMonths(), periods);
        return new DuePreview(start, due, deadlines.initialInterviewDeadline(start, reevaluation),
                reevaluation ? REEVALUATION_MONTHS : schema.getPeriodMonths(), schema.getVersionCode(),
                defaultSheet(employee), employee.getSupervisorId(), officeCity(employee.getOfficeId()));
    }

    /** Pola formularza: miejscowość i okres oceny „od–do” przed zamknięciem cyklu (oceniający / zastępca / kadry). */
    public EvaluationCycle updateDetails(Long id, User user, String place, LocalDate startDate, LocalDate dueDate,
                                         String reason) {
        EvaluationCycle cycle = load(id);
        boolean hr = user.getRole() == Role.HR && user.getOfficeId().equals(cycle.getOfficeId());
        if (!hr && !canActAsEvaluator(cycle, user)) {
            throw forbidden("Dane formularza zmienia oceniający lub kadry");
        }
        if (!cycle.getStatus().isActive()) {
            throw conflict("Cykl jest zamknięty — zmiana wyłącznie w trybie korekty");
        }
        LocalDate newStart = startDate != null ? startDate : cycle.getStartDate();
        LocalDate newDue = dueDate != null ? dueDate : cycle.getDueDate();
        if (newDue.isBefore(newStart)) {
            throw new RuleViolationException(List.of("Termin oceny nie może być wcześniejszy niż początek okresu oceny"));
        }
        boolean periodChanged = !newStart.equals(cycle.getStartDate()) || !newDue.equals(cycle.getDueDate());
        if (periodChanged && cycle.getStatus() != CycleStatus.DRAFT_CRITERIA && (reason == null || reason.isBlank())) {
            throw new RuleViolationException(List.of("Zmiana okresu oceny po potwierdzeniu kryteriów wymaga uzasadnienia"));
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        change(changes, "place", cycle.getPlace(), place == null ? cycle.getPlace() : place.trim(), cycle::setPlace);
        change(changes, "startDate", cycle.getStartDate(), newStart, cycle::setStartDate);
        change(changes, "dueDate", cycle.getDueDate(), newDue, cycle::setDueDate);
        if (!changes.isEmpty()) {
            audit.log(user, cycle.getOfficeId(), "CYCLE_DETAILS_UPDATED", AuditService.CYCLE, cycle.getId(),
                    auditPayload(user, cycle, payload("changes", changes, "reason", reason)));
        }
        return cycle;
    }

    public record CorrectionFields(LocalDate criteriaInterviewDate, LocalDate criteriaConfirmedOn,
                                   LocalDate evaluationInterviewDate, LocalDate evaluatedOn, LocalDate acknowledgedAt,
                                   String place, LocalDate startDate, LocalDate dueDate) {
    }

    /**
     * Tryb korekty (schemat pól KPRM: „do zmiany wyłącznie w trybie korekty błędów przez administratora”): cofnięcie
     * zablokowanego etapu albo korekta pól formalnych — zawsze z uzasadnieniem i wpisem ADMIN_CORRECTION w audycie.
     */
    public EvaluationCycle adminCorrection(Long id, User user, CycleStatus target, String reason, CorrectionFields f) {
        EvaluationCycle cycle = load(id);
        if (user.getRole() != Role.KPRM_ADMIN) {
            requireRole(user, Role.HR, Role.DG);
            requireSameOffice(user, cycle.getOfficeId());
        }
        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException(List.of("Korekta wymaga uzasadnienia"));
        }
        CycleStatus from = cycle.getStatus();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (target != null && target != from) {
            if (!from.canCorrectTo(target)) {
                throw conflict("Korekta " + from + " -> " + target + " nie jest dozwolona");
            }
            boolean reopens = !from.isActive() && target.isActive();
            if (reopens && cycles.findByEmployeeIdOrderByIdAsc(cycle.getEmployeeId()).stream()
                    .anyMatch(c -> !c.getId().equals(cycle.getId()) && c.getStatus().isActive())) {
                throw conflict("Pracownik ma już inny aktywny cykl — korekta otworzyłaby dwa cykle jednocześnie");
            }
            rollBack(cycle, target, changes);
            cycle.setStatus(target);
        }
        if (f != null) {
            change(changes, "criteriaInterviewDate", cycle.getCriteriaInterviewDate(), f.criteriaInterviewDate(),
                    cycle::setCriteriaInterviewDate);
            change(changes, "criteriaConfirmedOn", cycle.getCriteriaConfirmedOn(), f.criteriaConfirmedOn(),
                    cycle::setCriteriaConfirmedOn);
            change(changes, "evaluationInterviewDate", cycle.getEvaluationInterviewDate(), f.evaluationInterviewDate(),
                    cycle::setEvaluationInterviewDate);
            change(changes, "evaluatedOn", cycle.getEvaluatedOn(), f.evaluatedOn(), cycle::setEvaluatedOn);
            change(changes, "acknowledgedAt", cycle.getAcknowledgedAt(), f.acknowledgedAt(), cycle::setAcknowledgedAt);
            change(changes, "place", cycle.getPlace(), f.place(), cycle::setPlace);
            change(changes, "startDate", cycle.getStartDate(), f.startDate(), cycle::setStartDate);
            change(changes, "dueDate", cycle.getDueDate(), f.dueDate(), cycle::setDueDate);
        }
        if (changes.isEmpty() && from == cycle.getStatus()) {
            throw conflict("Brak zmian do skorygowania");
        }
        if (cycle.getDueDate().isBefore(cycle.getStartDate())) {
            throw new RuleViolationException(List.of("Termin oceny nie może być wcześniejszy niż początek okresu oceny"));
        }
        audit.log(user, cycle.getOfficeId(), "ADMIN_CORRECTION", AuditService.CYCLE, cycle.getId(),
                payload("reason", reason.trim(), "from", from, "to", cycle.getStatus(), "changes", changes));
        return cycle;
    }

    /** Skutki cofnięcia etapu: odblokowanie kryteriów albo unieważnienie sporządzenia / zapoznania. */
    private void rollBack(EvaluationCycle cycle, CycleStatus target, Map<String, Object> changes) {
        if (target == CycleStatus.DRAFT_CRITERIA) {
            change(changes, "criteriaConfirmedOn", cycle.getCriteriaConfirmedOn(), null, cycle::setCriteriaConfirmedOn, true);
        }
        if (cycle.getStatus() == CycleStatus.COMPLETED) {
            history.removeForCycle(cycle.getId());
            change(changes, "acknowledgedAt", cycle.getAcknowledgedAt(), null, cycle::setAcknowledgedAt, true);
            cycle.setAppealDeadline(null);
            cycle.setRefusalDate(null);
            cycle.setRefusalReason(null);
            cycle.setSignatureMethod(null);
        }
        if (target == CycleStatus.EVALUATION_DRAFT) {
            change(changes, "evaluatedOn", cycle.getEvaluatedOn(), null, cycle::setEvaluatedOn, true);
            cycle.setAverageScore(null);
            cycle.setPerformanceLevel(null);
            cycle.setFinalResult(null);
            cycle.setResultExplanation(null);
        }
    }

    private static <T> void change(Map<String, Object> changes, String field, T current, T requested,
                                   java.util.function.Consumer<T> setter) {
        change(changes, field, current, requested, setter, false);
    }

    private static <T> void change(Map<String, Object> changes, String field, T current, T requested,
                                   java.util.function.Consumer<T> setter, boolean allowNull) {
        if ((requested != null || allowNull) && !Objects.equals(current, requested)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("from", current);
            diff.put("to", requested);
            changes.put(field, diff);
            setter.accept(requested);
        }
    }

    private void requireEvaluatorFor(User employee, Long evaluatorId) {
        User evaluator = users.findById(evaluatorId).orElseThrow(() -> notFound("Użytkownik", evaluatorId));
        if (!evaluator.getOfficeId().equals(employee.getOfficeId()) || !evaluator.isActive()
                || (evaluator.getRole() != Role.EVALUATOR && evaluator.getRole() != Role.DG)
                || evaluator.getId().equals(employee.getId())) {
            throw conflict("Oceniający musi być aktywnym przełożonym (EVALUATOR lub DG) z tego samego urzędu");
        }
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
                auditPayload(user, cycle, Map.of("criteria", all.stream().map(Criterion::code).toList(),
                        "interviewDate", cycle.getCriteriaInterviewDate())));
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
                auditPayload(user, cycle, Map.of("noteId", saved.getId(), "isSharedAfterEval", sharedAfterEval)));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeNote> notes(Long id, User user) {
        EvaluationCycle cycle = get(id, user);
        List<EmployeeNote> all = notes.findByCycleIdOrderByCreatedAtAsc(cycle.getId());
        if (canActAsEvaluator(cycle, user)) {
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

    /** Dane poprzedniej oceny wpisywane w arkuszu, gdy brak ich w historii (schemat pól, cz. II). */
    public record PreviousEvaluationInput(EvaluationOutcome outcome, PerformanceLevel level, LocalDate evaluatedOn) {
    }

    public EvaluationResult evaluate(Long id, User user, Map<String, Integer> scores, String justification,
                                     String iprzNotes, GradeRequest gradeRequest, LocalDate interviewDate,
                                     boolean submit, String delayReason, SignatureMethod signatureMethod) {
        return evaluate(id, user, scores, justification, iprzNotes, gradeRequest, interviewDate, submit, delayReason,
                signatureMethod, null);
    }

    public EvaluationResult evaluate(Long id, User user, Map<String, Integer> scores, String justification,
                                     String iprzNotes, GradeRequest gradeRequest, LocalDate interviewDate,
                                     boolean submit, String delayReason, SignatureMethod signatureMethod,
                                     PreviousEvaluationInput previous) {
        EvaluationCycle cycle = asEvaluator(id, user);
        if (previous != null) {
            applyPreviousEvaluation(cycle, previous);
        }
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
                    auditPayload(user, cycle, payload("dueDate", cycle.getDueDate(), "overdueDays", overdueDays,
                            "delayReason", cycle.getDelayReason())));
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
        if (!canActAsEvaluator(cycle, user) && !hr) {
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

    private void applyPreviousEvaluation(EvaluationCycle cycle, PreviousEvaluationInput previous) {
        if (previous.outcome() == null) {
            cycle.setPrevEvaluationOutcome(null);
            cycle.setPrevEvaluationLevel(null);
            cycle.setPrevEvaluationDate(null);
            return;
        }
        if (previous.level() == null || previous.evaluatedOn() == null) {
            throw new RuleViolationException(List.of("Poprzednia ocena wymaga podania poziomu i daty sporządzenia"));
        }
        if (previous.evaluatedOn().isAfter(cycle.getStartDate())) {
            throw new RuleViolationException(List.of("Poprzednia ocena musi być sporządzona przed początkiem okresu oceny"));
        }
        cycle.setPrevEvaluationOutcome(previous.outcome());
        cycle.setPrevEvaluationLevel(previous.level());
        cycle.setPrevEvaluationDate(previous.evaluatedOn());
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
        Map<String, Object> p = auditPayload(actor, cycle, extra);
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
        if (!canActAsEvaluator(cycle, user)) {
            throw forbidden("Operację wykonuje wyłącznie oceniający (lub jego aktywny zastępca)");
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
