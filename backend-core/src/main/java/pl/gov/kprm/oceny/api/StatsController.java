package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.StatsService;
import pl.gov.kprm.oceny.service.StatsService.Summary;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService stats;

    @GetMapping("/summary")
    public Summary summary(@CurrentUser User user) {
        return stats.summary(user);
    }
}
