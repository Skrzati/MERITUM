package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Dtos.DemoUserDto;
import pl.gov.kprm.oceny.api.Dtos.LoginResponse;
import pl.gov.kprm.oceny.api.Dtos.UserDto;
import pl.gov.kprm.oceny.api.Requests.GovLoginRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.auth.JwtService;
import pl.gov.kprm.oceny.domain.Office;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.service.Access;
import pl.gov.kprm.oceny.service.AuditService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    /** Symulacja logowania przez Węzeł Krajowy / Profil Zaufany — środowisko demonstracyjne. */
    static final String IDENTITY_PROVIDER = "SIMULATED_WK_PZ";

    private final UserRepository users;
    private final OfficeRepository offices;
    private final JwtService jwt;
    private final AuditService audit;

    @GetMapping("/demo-users")
    public List<DemoUserDto> demoUsers() {
        Map<Long, Office> byId = offices.findAll().stream().collect(Collectors.toMap(Office::getId, Function.identity()));
        return users.findAllByActiveTrueOrderByIdAsc().stream().map(u -> DemoUserDto.of(u, byId.get(u.getOfficeId()))).toList();
    }

    @PostMapping("/gov-login")
    public LoginResponse govLogin(@Valid @RequestBody GovLoginRequest req) {
        User user = users.findById(req.userId()).orElseThrow(() -> Access.notFound("Użytkownik", req.userId()));
        if (!user.isActive()) {
            throw Access.forbidden("Konto jest nieaktywne");
        }
        audit.log(user, user.getOfficeId(), "LOGIN", AuditService.USER, user.getId(), Map.of("idp", IDENTITY_PROVIDER));
        return new LoginResponse(jwt.issue(user, IDENTITY_PROVIDER), "Bearer", jwt.ttlSeconds(), IDENTITY_PROVIDER,
                UserDto.of(user));
    }

    @GetMapping("/me")
    public UserDto me(@CurrentUser User user) {
        return UserDto.of(user);
    }
}
