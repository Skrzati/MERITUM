package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.AiProxyService;

/** /api/core-ai/{socratic-draft|consistency-check|hr-partner|interview-prep} — wywołanie AI z audytem (AI Act art. 26). */
@RestController
@RequestMapping("/api/core-ai")
@RequiredArgsConstructor
public class AiProxyController {
    private final AiProxyService ai;

    @PostMapping("/{operation}")
    public JsonNode call(@PathVariable String operation, @CurrentUser User user, @RequestBody JsonNode body,
                         @RequestParam(required = false) Long cycleId) {
        return ai.call(operation, body, user, cycleId);
    }
}
