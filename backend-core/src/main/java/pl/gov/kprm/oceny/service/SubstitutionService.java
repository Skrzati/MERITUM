package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EvaluatorSubstitution;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EvaluatorSubstitutionRepository;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.gov.kprm.oceny.service.Access.*;

/**
 * Zastępstwa oceniających (poradnik KPRM: przy długiej nieobecności ocenę może prowadzić zastępca). Aktywny zastępca
 * widzi aktywne cykle zastępowanego, prowadzi je i dostaje powiadomienia; jego akcje mają w audycie adnotację.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SubstitutionService {
    private static final Set<Role> EVALUATOR_ROLES = Set.of(Role.EVALUATOR, Role.DG);

    private final EvaluatorSubstitutionRepository substitutions;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public EvaluatorSubstitution create(User hr, Long evaluatorId, Long deputyId, LocalDate start, LocalDate end,
                                        String reason) {
        requireRole(hr, Role.HR);
        User evaluator = inOffice(hr, evaluatorId);
        User deputy = inOffice(hr, deputyId);
        if (evaluator.getId().equals(deputy.getId())) {
            throw conflict("Zastępca musi być inną osobą niż zastępowany oceniający");
        }
        if (!EVALUATOR_ROLES.contains(evaluator.getRole()) || !EVALUATOR_ROLES.contains(deputy.getRole())) {
            throw conflict("Zastępowany i zastępca muszą mieć rolę EVALUATOR lub DG");
        }
        if (end.isBefore(start)) {
            throw conflict("Data końca zastępstwa jest wcześniejsza niż początek");
        }
        EvaluatorSubstitution s = new EvaluatorSubstitution();
        s.setOfficeId(hr.getOfficeId());
        s.setEvaluatorId(evaluator.getId());
        s.setDeputyId(deputy.getId());
        s.setStartDate(start);
        s.setEndDate(end);
        s.setReason(reason);
        s.setCreatedBy(hr.getId());
        EvaluatorSubstitution saved = substitutions.saveAndFlush(s);
        audit.log(hr, hr.getOfficeId(), "SUBSTITUTION_CREATED", AuditService.USER, evaluator.getId(),
                Map.of("substitutionId", saved.getId(), "deputyId", deputy.getId(), "from", start, "to", end));
        return saved;
    }

    /** Zakończenie zastępstwa: przyszłe — usuwane, trwające — kończone wczoraj. */
    public void end(User hr, Long id) {
        requireRole(hr, Role.HR);
        EvaluatorSubstitution s = substitutions.findById(id).orElseThrow(() -> notFound("Zastępstwo", id));
        requireSameOffice(hr, s.getOfficeId());
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        if (s.getStartDate().isAfter(yesterday)) {
            substitutions.delete(s);
        } else if (s.getEndDate().isAfter(yesterday)) {
            s.setEndDate(yesterday);
        }
        audit.log(hr, hr.getOfficeId(), "SUBSTITUTION_ENDED", AuditService.USER, s.getEvaluatorId(),
                Map.of("substitutionId", id));
    }

    @Transactional(readOnly = true)
    public List<EvaluatorSubstitution> list(User user) {
        return user.getRole() == Role.HR || user.getRole() == Role.DG
                ? substitutions.findByOfficeIdOrderByStartDateDesc(user.getOfficeId())
                : substitutions.findByEvaluatorIdOrDeputyIdOrderByStartDateDesc(user.getId(), user.getId());
    }

    /** Oceniający, których użytkownik zastępuje dzisiaj. */
    @Transactional(readOnly = true)
    public Set<Long> substitutedBy(Long deputyId) {
        return substitutions.activeForDeputy(deputyId, LocalDate.now(clock)).stream()
                .map(EvaluatorSubstitution::getEvaluatorId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean isActiveDeputy(Long deputyId, Long evaluatorId) {
        return substitutedBy(deputyId).contains(evaluatorId);
    }

    private User inOffice(User hr, Long userId) {
        User u = users.findById(userId).orElseThrow(() -> notFound("Użytkownik", userId));
        requireSameOffice(hr, u.getOfficeId());
        return u;
    }
}
