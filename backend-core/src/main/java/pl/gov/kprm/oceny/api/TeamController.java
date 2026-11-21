package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.TeamService;
import pl.gov.kprm.oceny.service.TeamService.Overview;

/** „Mój zespół” — Wirtualny HR Partner dla przełożonego (EVALUATOR, DG). */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService team;

    @GetMapping("/overview")
    public Overview overview(@CurrentUser User user) {
        return team.overview(user);
    }
}
