package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.AuditDto;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.AuditService;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService audit;

    @GetMapping
    public List<AuditDto> recent(@CurrentUser User user, @RequestParam(defaultValue = "50") int limit) {
        return audit.recent(user, limit).stream().map(AuditDto::of).toList();
    }
}
