package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.rules.OverdueRules;
import pl.gov.kprm.oceny.service.CycleWorkflowService;
import pl.gov.kprm.oceny.service.EvaluationHistoryService;
import pl.gov.kprm.oceny.service.EvaluationHistoryService.GradeEligibility;

/** Buduje CycleDto z polami wyliczanymi przez reguły (termin kryteriów, art. 89 ust. 2). */
@Component
@RequiredArgsConstructor
public class CycleMapper {
    private final CycleWorkflowService workflow;
    private final EvaluationHistoryService history;
    private final Clock clock;

    /** Dni po terminie dla cyklu, w którym ocena nie została jeszcze sporządzona. */
    public long overdueDays(EvaluationCycle cycle) {
        return OverdueRules.overdueDays(cycle.getDueDate(), LocalDate.now(clock),
                cycle.getStatus().isActive() && cycle.getEvaluatedOn() == null);
    }


    public CycleDto toDto(EvaluationCycle cycle) {
        GradeEligibility grade = history.mandatoryGrade(cycle);
        return CycleDto.of(cycle, workflow.criteriaDeadline(cycle), grade.eligible(), grade.deadline(),
                overdueDays(cycle));
    }
}
