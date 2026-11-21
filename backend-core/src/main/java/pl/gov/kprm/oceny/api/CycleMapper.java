package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Office;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.rules.OverdueRules;
import pl.gov.kprm.oceny.service.CycleWorkflowService;
import pl.gov.kprm.oceny.service.EvaluationHistoryService;
import pl.gov.kprm.oceny.service.EvaluationHistoryService.CycleHistory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buduje CycleDto z polami wyliczanymi przez reguły (termin kryteriów, art. 89 ust. 2, organ odwoławczy, poprzednia
 * ocena). Listy budowane zbiorczo: jedno zapytanie o historię, użytkowników i urzędy dla wszystkich cykli.
 */
@Component
@RequiredArgsConstructor
public class CycleMapper {
    private final CycleWorkflowService workflow;
    private final EvaluationHistoryService history;
    private final OfficeRepository offices;
    private final Clock clock;

    /** Dni po terminie dla cyklu, w którym ocena nie została jeszcze sporządzona. */
    public long overdueDays(EvaluationCycle cycle) {
        return OverdueRules.overdueDays(cycle.getDueDate(), LocalDate.now(clock),
                cycle.getStatus().isActive() && cycle.getEvaluatedOn() == null);
    }

    public CycleDto toDto(EvaluationCycle cycle) {
        return toDtos(List.of(cycle)).get(0);
    }

    public List<CycleDto> toDtos(List<EvaluationCycle> cycles) {
        Map<Long, CycleHistory> histories = history.forCycles(cycles);
        Set<Long> officeIds = cycles.stream().map(EvaluationCycle::getOfficeId).collect(Collectors.toSet());
        Map<Long, Office> byId = offices.findAllById(officeIds).stream()
                .collect(Collectors.toMap(Office::getId, Function.identity()));
        return cycles.stream().map(c -> {
            CycleHistory h = histories.get(c.getId());
            Office office = byId.get(c.getOfficeId());
            return CycleDto.of(c, new CycleDto.Facts(workflow.criteriaDeadline(c), h.grade().eligible(),
                    h.grade().deadline(), overdueDays(c), office == null || office.isHasDgRole(), h.previous()));
        }).toList();
    }
}
