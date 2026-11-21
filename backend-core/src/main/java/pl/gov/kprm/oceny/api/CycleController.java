package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;

import java.util.List;

@RestController
@RequestMapping("/api/cycles")
@RequiredArgsConstructor
public class CycleController {
    private final EvaluationCycleRepository cycles;

    @GetMapping
    public List<CycleDto> myCycles(@CurrentUser User user) {
        List<EvaluationCycle> result = switch (user.getRole()) {
            case KPRM_ADMIN -> cycles.findAllByOrderByIdAsc();
            case HR, DG -> cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId());
            case EVALUATOR, EVALUATED -> cycles.findByEmployeeIdOrEvaluatorIdOrderByIdAsc(user.getId(), user.getId());
        };
        return result.stream().map(CycleDto::of).toList();
    }
}
