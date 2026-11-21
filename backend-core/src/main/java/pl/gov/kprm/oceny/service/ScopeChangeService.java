package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.ScopeChangeRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.DeadlineService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Zmiana stanowiska z istotną zmianą zakresu obowiązków:
 * <ul>
 *   <li>§ 9 ust. 1 — do 6 mies. od ustalenia kryteriów (pierwsza ocena) / od ostatniej oceny: ponowne ustalenie
 *       kryteriów w 30 dni, termin oceny bez zmian;</li>
 *   <li>art. 81 ust. 6 i § 9 ust. 2 — po 6 mies.: ocena nie wcześniej niż 7 dni przed i nie później niż 30 dni po zmianie.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ScopeChangeService {
    private final CycleWorkflowService workflow;
    private final ScopeChangeRepository changes;
    private final EvaluationHistoryService history;
    private final UserRepository users;
    private final DeadlineService deadlines;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ScopeChange> list(Long cycleId, User user) {
        return changes.findByCycleIdOrderByChangeDateAsc(workflow.get(cycleId, user).getId());
    }

    public ScopeChange record(Long cycleId, User user, LocalDate changeDate, String newPositionTitle,
                              String description, boolean significant) {
        EvaluationCycle cycle = workflow.load(cycleId);
        boolean hr = user.getRole() == Role.HR && user.getOfficeId().equals(cycle.getOfficeId());
        if (!hr && !workflow.canActAsEvaluator(cycle, user)) {
            throw forbidden("Zmianę zakresu obowiązków rejestruje kadra lub oceniający");
        }
        if (!cycle.getStatus().isActive()) {
            throw conflict("Cykl jest zakończony");
        }
        ScopeChange change = new ScopeChange();
        change.setCycleId(cycle.getId());
        change.setUserId(cycle.getEmployeeId());
        change.setChangeDate(changeDate);
        change.setNewPositionTitle(newPositionTitle);
        change.setDescription(description);
        change.setSignificant(significant);
        change.setRecordedBy(user.getId());
        change.setOutcome(ScopeChangeOutcome.NONE);

        if (significant) {
            LocalDate actionDeadline = deadlines.shiftToWorkingDay(changeDate.plusDays(DeadlineService.SCOPE_CHANGE_DAYS));
            change.setActionDeadline(actionDeadline);
            if (!changeDate.isAfter(referenceDate(cycle).plusMonths(DeadlineService.SIX_MONTHS))) {
                change.setOutcome(ScopeChangeOutcome.CRITERIA_RESET);
                if (cycle.getStatus() == CycleStatus.OBSERVATION) {
                    cycle.setCriteriaConfirmedOn(null);
                    workflow.transition(cycle, user, CycleStatus.DRAFT_CRITERIA, "CRITERIA_RESET_SCOPE_CHANGE",
                            Map.of("criteriaDeadline", actionDeadline));
                }
            } else {
                change.setOutcome(ScopeChangeOutcome.EVALUATION_REQUIRED);
                if (actionDeadline.isBefore(cycle.getDueDate())) {
                    cycle.setDueDate(actionDeadline);
                }
            }
        }
        if (newPositionTitle != null && !newPositionTitle.isBlank()) {
            users.findById(cycle.getEmployeeId()).ifPresent(u -> u.setPositionTitle(newPositionTitle));
        }
        ScopeChange saved = changes.saveAndFlush(change);
        audit.log(user, cycle.getOfficeId(), "SCOPE_CHANGE_RECORDED", AuditService.CYCLE, cycle.getId(),
                CycleWorkflowService.payload("significant", significant, "outcome", saved.getOutcome(),
                        "actionDeadline", saved.getActionDeadline()));
        return saved;
    }

    /** Pierwsza ocena: dzień ustalenia kryteriów; kolejna: dzień sporządzenia ostatniej oceny (= początek cyklu). */
    private LocalDate referenceDate(EvaluationCycle cycle) {
        boolean first = !history.hasEvaluationBefore(cycle.getEmployeeId(), cycle.getStartDate());
        if (first && cycle.getCriteriaInterviewDate() != null) {
            return cycle.getCriteriaInterviewDate();
        }
        return cycle.getStartDate();
    }
}
