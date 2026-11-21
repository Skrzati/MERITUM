package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Requests.WellbeingRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.WellbeingService;

import java.util.Map;

@RestController
@RequestMapping("/api/wellbeing")
@RequiredArgsConstructor
public class WellbeingController {
    private final WellbeingService wellbeing;

    /** Dobrowolna ankieta — odpowiedź nie trafia do oceny ani do przełożonego; tylko statystyki zbiorcze. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@CurrentUser User user, @Valid @RequestBody WellbeingRequest req) {
        wellbeing.submit(user, req.workload(), req.energy(), req.support(), req.wantsConversation());
        return Map.of("status", "RECEIVED",
                "info", "Dziękujemy. Odpowiedzi są wykorzystywane wyłącznie zbiorczo i nie wpływają na ocenę okresową.");
    }
}
