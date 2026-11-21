package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.SubstitutionDto;
import pl.gov.kprm.oceny.api.Requests.SubstitutionRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.SubstitutionService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Zastępstwa oceniających: kadry ustawiają, oceniający i zastępca widzą swoje, HR/DG — cały urząd. */
@RestController
@RequestMapping("/api/substitutions")
@RequiredArgsConstructor
public class SubstitutionController {
    private final SubstitutionService substitutions;
    private final Clock clock;

    @GetMapping
    public List<SubstitutionDto> list(@CurrentUser User user) {
        LocalDate today = LocalDate.now(clock);
        return substitutions.list(user).stream().map(s -> SubstitutionDto.of(s, today)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubstitutionDto create(@CurrentUser User user, @Valid @RequestBody SubstitutionRequest req) {
        return SubstitutionDto.of(substitutions.create(user, req.evaluatorId(), req.deputyId(), req.startDate(),
                req.endDate(), req.reason()), LocalDate.now(clock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@CurrentUser User user, @PathVariable Long id) {
        substitutions.end(user, id);
    }
}
