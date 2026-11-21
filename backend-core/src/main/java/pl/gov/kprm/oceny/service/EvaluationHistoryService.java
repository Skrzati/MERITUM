package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.EvaluationHistoryRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.DeadlineService;
import pl.gov.kprm.oceny.rules.GradeRules;
import pl.gov.kprm.oceny.rules.HistoryEntry;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Historia ocen: dane „poprzedniej oceny” na arkuszu i podstawa obligatoryjnego stopnia (art. 89 ust. 2). */
@Service
@Transactional
@RequiredArgsConstructor
public class EvaluationHistoryService {
    private final EvaluationHistoryRepository history;
    private final UserRepository users;
    private final DeadlineService deadlines;

    public record GradeEligibility(boolean eligible, LocalDate deadline) {
        static final GradeEligibility NONE = new GradeEligibility(false, null);
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

    @Transactional(readOnly = true)
    public Optional<EvaluationHistory> previousEvaluation(EvaluationCycle cycle) {
        return history.findByUserIdOrderByEvaluatedOnAsc(cycle.getEmployeeId()).stream()
                .filter(h -> !cycle.getId().equals(h.getCycleId()))
                .filter(h -> !h.getEvaluatedOn().isAfter(cycle.getStartDate()))
                .max(Comparator.comparing(EvaluationHistory::getEvaluatedOn));
    }

    @Transactional(readOnly = true)
    public boolean hasEvaluationBefore(Long userId, LocalDate date) {
        return history.findByUserIdOrderByEvaluatedOnAsc(userId).stream().anyMatch(h -> !h.getEvaluatedOn().isAfter(date));
    }

    /** Ocena tego cyklu (wraz z poprzednią) uprawnia do obligatoryjnego kolejnego stopnia służbowego. */
    @Transactional(readOnly = true)
    public GradeEligibility mandatoryGrade(EvaluationCycle cycle) {
        if (cycle.getStatus() != CycleStatus.COMPLETED || cycle.getEvaluatedOn() == null) {
            return GradeEligibility.NONE;
        }
        User employee = users.findById(cycle.getEmployeeId()).orElse(null);
        if (employee == null) {
            return GradeEligibility.NONE;
        }
        List<HistoryEntry> upToCycle = history.findByUserIdOrderByEvaluatedOnAsc(employee.getId()).stream()
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
