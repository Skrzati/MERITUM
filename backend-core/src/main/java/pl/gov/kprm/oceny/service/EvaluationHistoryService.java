package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.EvaluationHistoryRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.DeadlineService;
import pl.gov.kprm.oceny.rules.EvaluationOutcome;
import pl.gov.kprm.oceny.rules.GradeRules;
import pl.gov.kprm.oceny.rules.HistoryEntry;
import pl.gov.kprm.oceny.rules.PerformanceLevel;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pl.gov.kprm.oceny.service.Access.*;

/** Historia ocen: dane „poprzedniej oceny” na arkuszu i podstawa obligatoryjnego stopnia (art. 89 ust. 2). */
@Service
@Transactional
@RequiredArgsConstructor
public class EvaluationHistoryService {
    /** Źródło danych poprzedniej oceny wpisanych ręcznie w arkuszu (brak wpisu w historii). */
    public static final String MANUAL_SOURCE = "MANUAL";

    private final EvaluationHistoryRepository history;
    private final UserRepository users;
    private final DeadlineService deadlines;
    private final AuditService audit;
    private final Clock clock;

    public record GradeEligibility(boolean eligible, LocalDate deadline) {
        static final GradeEligibility NONE = new GradeEligibility(false, null);
    }

    public record PreviousEvaluation(EvaluationOutcome outcome, PerformanceLevel level, LocalDate evaluatedOn,
                                     String source) {
    }

    /** Dane historyczne potrzebne w widoku cyklu — liczone zbiorczo (bez zapytań per cykl). */
    public record CycleHistory(GradeEligibility grade, PreviousEvaluation previous) {
    }

    /** Zapisuje (lub aktualizuje po zmianie oceny przez DG) wpis historii dla zakończonego cyklu. */
    public void recordCompleted(EvaluationCycle cycle) {
        EvaluationHistory entry = history.findByCycleId(cycle.getId()).orElseGet(EvaluationHistory::new);
        entry.setUserId(cycle.getEmployeeId());
        entry.setOfficeId(cycle.getOfficeId());
        entry.setCycleId(cycle.getId());
        entry.setOutcome(cycle.getFinalResult());
        entry.setLevel(cycle.getPerformanceLevel());
        entry.setEvaluatedOn(cycle.getEvaluatedOn() != null ? cycle.getEvaluatedOn() : cycle.getAcknowledgedAt());
        entry.setSource(HistorySource.SYSTEM);
        history.save(entry);
    }

    public void removeForCycle(Long cycleId) {
        history.findByCycleId(cycleId).ifPresent(history::delete);
    }

    /** Ocena sprzed wdrożenia systemu wprowadzana przez kadry (przypadek użycia „oceny historyczne”). */
    public EvaluationHistory addHistorical(User hr, User employee, EvaluationOutcome outcome, PerformanceLevel level,
                                           LocalDate evaluatedOn) {
        if (evaluatedOn.isAfter(LocalDate.now(clock))) {
            throw conflict("Data oceny historycznej nie może być z przyszłości");
        }
        EvaluationHistory entry = new EvaluationHistory();
        entry.setUserId(employee.getId());
        entry.setOfficeId(employee.getOfficeId());
        entry.setOutcome(outcome);
        entry.setLevel(level);
        entry.setEvaluatedOn(evaluatedOn);
        entry.setSource(HistorySource.HISTORICAL);
        entry.setEnteredBy(hr.getId());
        EvaluationHistory saved = history.saveAndFlush(entry);
        audit.log(hr, employee.getOfficeId(), "HISTORY_ADDED", AuditService.USER, employee.getId(),
                Map.of("historyId", saved.getId(), "outcome", outcome, "level", level, "evaluatedOn", evaluatedOn));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EvaluationHistory> of(Long userId) {
        return history.findByUserIdOrderByEvaluatedOnAsc(userId);
    }

    /** Poprzednia ocena dla wydruku: z historii, a w razie jej braku — wpisana ręcznie w arkuszu. */
    @Transactional(readOnly = true)
    public Optional<EvaluationHistory> previousEvaluation(EvaluationCycle cycle) {
        PreviousEvaluation prev = previous(cycle, history.findByUserIdOrderByEvaluatedOnAsc(cycle.getEmployeeId()));
        if (prev == null) {
            return Optional.empty();
        }
        EvaluationHistory view = new EvaluationHistory();
        view.setOutcome(prev.outcome());
        view.setLevel(prev.level());
        view.setEvaluatedOn(prev.evaluatedOn());
        return Optional.of(view);
    }

    @Transactional(readOnly = true)
    public boolean hasEvaluationBefore(Long userId, LocalDate date) {
        return history.findByUserIdOrderByEvaluatedOnAsc(userId).stream().anyMatch(h -> !h.getEvaluatedOn().isAfter(date));
    }

    @Transactional(readOnly = true)
    public GradeEligibility mandatoryGrade(EvaluationCycle cycle) {
        return forCycles(List.of(cycle)).get(cycle.getId()).grade();
    }

    /** Dwa zapytania (użytkownicy + historia) dla całej listy cykli zamiast dwóch na każdy cykl. */
    @Transactional(readOnly = true)
    public Map<Long, CycleHistory> forCycles(Collection<EvaluationCycle> cycles) {
        if (cycles.isEmpty()) {
            return Map.of();
        }
        Set<Long> employeeIds = cycles.stream().map(EvaluationCycle::getEmployeeId).collect(Collectors.toSet());
        Map<Long, User> employees = users.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<EvaluationHistory>> byUser = history.findByUserIdInOrderByEvaluatedOnAsc(employeeIds).stream()
                .collect(Collectors.groupingBy(EvaluationHistory::getUserId));
        Map<Long, CycleHistory> result = new HashMap<>();
        for (EvaluationCycle c : cycles) {
            List<EvaluationHistory> entries = byUser.getOrDefault(c.getEmployeeId(), List.of());
            result.put(c.getId(), new CycleHistory(mandatoryGrade(c, employees.get(c.getEmployeeId()), entries),
                    previous(c, entries)));
        }
        return result;
    }

    private static PreviousEvaluation previous(EvaluationCycle cycle, List<EvaluationHistory> entries) {
        return entries.stream()
                .filter(h -> !cycle.getId().equals(h.getCycleId()))
                .filter(h -> !h.getEvaluatedOn().isAfter(cycle.getStartDate()))
                .max(Comparator.comparing(EvaluationHistory::getEvaluatedOn))
                .map(h -> new PreviousEvaluation(h.getOutcome(), h.getLevel(), h.getEvaluatedOn(), h.getSource().name()))
                .orElseGet(() -> cycle.getPrevEvaluationOutcome() == null ? null
                        : new PreviousEvaluation(cycle.getPrevEvaluationOutcome(), cycle.getPrevEvaluationLevel(),
                        cycle.getPrevEvaluationDate(), MANUAL_SOURCE));
    }

    private GradeEligibility mandatoryGrade(EvaluationCycle cycle, User employee, List<EvaluationHistory> entries) {
        if (cycle.getStatus() != CycleStatus.COMPLETED || cycle.getEvaluatedOn() == null || employee == null) {
            return GradeEligibility.NONE;
        }
        List<HistoryEntry> upToCycle = entries.stream()
                .filter(h -> !h.getEvaluatedOn().isAfter(cycle.getEvaluatedOn()))
                .map(h -> new HistoryEntry(h.getOutcome(), h.getLevel(), h.getEvaluatedOn()))
                .toList();
        boolean thisIsTop = new HistoryEntry(cycle.getFinalResult(), cycle.getPerformanceLevel(), cycle.getEvaluatedOn())
                .isTopTwoPositive();
        boolean eligible = thisIsTop && GradeRules.mandatoryGradeEligible(employee.isCivilServant(),
                employee.getCivilServiceGrade(), employee.getLastGradeDate(), upToCycle);
        return eligible && cycle.getAcknowledgedAt() != null
                ? new GradeEligibility(true, deadlines.mandatoryGradeDeadline(cycle.getAcknowledgedAt()))
                : new GradeEligibility(eligible, null);
    }
}
