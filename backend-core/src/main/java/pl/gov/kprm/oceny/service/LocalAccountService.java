package pl.gov.kprm.oceny.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.RuleViolationException;
import pl.gov.kprm.oceny.service.HrService.ImportResult;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Tryb Mały Urząd: konta lokalne (login = e-mail, hasło BCrypt) obok WK/PZ i AD, ręczne dodawanie kont i import CSV.
 * Administrator KPRM zarządza dowolnym urzędem, kadry — własnym.
 */
@Service
@Transactional
public class LocalAccountService {
    static final int MIN_PASSWORD_LENGTH = 12;
    static final String INVALID_CREDENTIALS = "Nieprawidłowy login lub hasło";
    private static final Set<Role> CREATABLE_ROLES = EnumSet.of(Role.EVALUATOR, Role.EVALUATED, Role.HR, Role.DG);

    private final UserRepository users;
    private final OfficeRepository offices;
    private final HrService hr;
    private final AuditService audit;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder;
    /** Porównanie z fikcyjnym skrótem, gdy konta nie ma — czas odpowiedzi nie zdradza istnienia loginu. */
    private final String dummyHash;

    public LocalAccountService(UserRepository users, OfficeRepository offices, HrService hr, AuditService audit,
                               Clock clock, @Value("${app.auth.bcrypt-strength:12}") int strength) {
        this.users = users;
        this.offices = offices;
        this.hr = hr;
        this.audit = audit;
        this.clock = clock;
        this.encoder = new BCryptPasswordEncoder(strength);
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** Logowanie lokalne: wyłącznie aktywne konto z hasłem, w urzędzie z włączonym trybem kont lokalnych. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User login(String login, String password) {
        Optional<User> found = users.findByEmailIgnoreCase(login.trim());
        String hash = found.flatMap(User::passwordHash).orElse(dummyHash);
        boolean matches = encoder.matches(password, hash);
        User user = found.orElse(null);
        if (!matches || user == null || !user.isActive() || !user.hasLocalPassword() || !localAuthEnabled(user.getOfficeId())) {
            if (user != null) {
                audit.log(user, user.getOfficeId(), "LOGIN_FAILED", AuditService.USER, user.getId(), Map.of("idp", "LOCAL"));
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        return user;
    }

    public void setPassword(User actor, Long userId, String password) {
        User target = managed(actor, userId);
        requireStrong(password);
        target.setPasswordHash(encoder.encode(password));
        target.setPasswordChangedAt(OffsetDateTime.now(clock));
        audit.log(actor, target.getOfficeId(), "PASSWORD_SET", AuditService.USER, target.getId(), Map.of());
    }

    /** Łączenie ról w małym urzędzie (np. kierownik urzędu: DG + EVALUATOR + HR). */
    public User setSecondaryRoles(User actor, Long userId, Set<Role> roles) {
        User target = managed(actor, userId);
        target.setSecondaryRoles(HrService.secondaryRoles(roles == null ? Set.of() : roles, target.getRole()));
        audit.log(actor, target.getOfficeId(), "SECONDARY_ROLES_SET", AuditService.USER, target.getId(),
                Map.of("secondaryRoles", target.getSecondaryRoles()));
        return target;
    }

    public record NewAccount(Long officeId, String firstName, String lastName, String email, Role role,
                             EmploymentStatus employmentStatus, Long supervisorId, String department,
                             String positionTitle, String password, Set<Role> secondaryRoles) {
        public NewAccount(Long officeId, String firstName, String lastName, String email, Role role,
                          EmploymentStatus employmentStatus, Long supervisorId, String department,
                          String positionTitle, String password) {
            this(officeId, firstName, lastName, email, role, employmentStatus, supervisorId, department,
                    positionTitle, password, null);
        }
    }

    public User create(User actor, NewAccount a) {
        Long officeId = targetOffice(actor, a.officeId());
        if (!CREATABLE_ROLES.contains(a.role())) {
            throw forbidden("Rola " + a.role() + " nie może być nadana w urzędzie");
        }
        String email = a.email().trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@")) {
            throw new RuleViolationException(List.of("Niepoprawny adres e-mail"));
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw conflict("Konto o adresie " + email + " już istnieje");
        }
        User u = new User();
        u.setOfficeId(officeId);
        u.setFirstName(a.firstName().trim());
        u.setLastName(a.lastName().trim());
        u.setFullName(u.getFirstName() + " " + u.getLastName());
        u.setEmail(email);
        u.setRole(a.role());
        u.setEmploymentStatus(a.employmentStatus() != null ? a.employmentStatus() : EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY);
        u.setDepartment(blankToNull(a.department()));
        u.setPositionTitle(blankToNull(a.positionTitle()));
        if (a.secondaryRoles() != null) {
            u.setSecondaryRoles(HrService.secondaryRoles(a.secondaryRoles(), a.role()));
        }
        if (a.supervisorId() != null) {
            User supervisor = users.findById(a.supervisorId()).orElseThrow(() -> notFound("Użytkownik", a.supervisorId()));
            if (!supervisor.getOfficeId().equals(officeId)) {
                throw conflict("Przełożony musi być z tego samego urzędu");
            }
            u.setSupervisorId(supervisor.getId());
        }
        if (a.password() != null && !a.password().isEmpty()) {
            requireStrong(a.password());
            u.setPasswordHash(encoder.encode(a.password()));
            u.setPasswordChangedAt(OffsetDateTime.now(clock));
        }
        User saved = users.saveAndFlush(u);
        audit.log(actor, officeId, "ACCOUNT_CREATED", AuditService.USER, saved.getId(),
                Map.of("role", saved.getRole(), "localPassword", saved.hasLocalPassword()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<User> list(User actor, Long officeId) {
        return users.findByOfficeIdAndActiveTrueOrderByIdAsc(targetOffice(actor, officeId));
    }

    public ImportResult importCsv(User actor, Long officeId, InputStream csv) throws IOException {
        return hr.importInto(actor, targetOffice(actor, officeId), csv);
    }

    @Transactional(readOnly = true)
    public List<Office> offices(User actor) {
        requireRole(actor, Role.KPRM_ADMIN, Role.HR);
        return actor.hasRole(Role.KPRM_ADMIN) ? offices.findAll()
                : offices.findById(actor.getOfficeId()).map(List::of).orElse(List.of());
    }

    /** Włączenie / wyłączenie trybu Mały Urząd (kont lokalnych) — wyłącznie administrator KPRM. */
    public Office setLocalAuth(User actor, Long officeId, boolean enabled) {
        requireRole(actor, Role.KPRM_ADMIN);
        Office office = offices.findById(officeId).orElseThrow(() -> notFound("Urząd", officeId));
        office.setLocalAuthEnabled(enabled);
        audit.log(actor, officeId, "LOCAL_AUTH_TOGGLED", AuditService.USER, null, Map.of("enabled", enabled));
        return office;
    }

    private boolean localAuthEnabled(Long officeId) {
        return offices.findById(officeId).map(Office::isLocalAuthEnabled).orElse(false);
    }

    private Long targetOffice(User actor, Long requested) {
        requireRole(actor, Role.KPRM_ADMIN, Role.HR);
        if (actor.hasRole(Role.HR)) {
            if (requested != null && !requested.equals(actor.getOfficeId())) {
                throw forbidden("Kadry zarządzają kontami wyłącznie własnego urzędu");
            }
            return actor.getOfficeId();
        }
        if (requested == null) {
            throw new RuleViolationException(List.of("Wskaż urząd (officeId)"));
        }
        return offices.findById(requested).map(Office::getId).orElseThrow(() -> notFound("Urząd", requested));
    }

    private User managed(User actor, Long userId) {
        User target = users.findById(userId).orElseThrow(() -> notFound("Użytkownik", userId));
        targetOffice(actor, target.getOfficeId());
        if (target.hasRole(Role.KPRM_ADMIN) && !actor.hasRole(Role.KPRM_ADMIN)) {
            throw forbidden("Kadry nie zarządzają kontem administratora KPRM");
        }
        if (!target.isActive()) {
            throw conflict("Konto jest zanonimizowane");
        }
        return target;
    }

    static void requireStrong(String password) {
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (password.length() < MIN_PASSWORD_LENGTH || !letter || !digit) {
            throw new RuleViolationException(List.of("Hasło: min. " + MIN_PASSWORD_LENGTH
                    + " znaków, w tym litera i cyfra"));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
