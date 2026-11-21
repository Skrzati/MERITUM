package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EscalationLevel;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.UserRepository;
import pl.gov.kprm.oceny.rules.OverdueRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Oceny po terminie: termin nie blokuje sporządzenia oceny, ale opóźnienie jest eskalowane. */
@Service
@RequiredArgsConstructor
public class OverdueService {
    private final EvaluationCycleRepository cycles;
    private final UserRepository users;
    private final Clock clock;

    public record OverdueItem(Long cycleId, Long employeeId, String employeeName, Long evaluatorId, String evaluatorName,
                              LocalDate dueDate, long overdueDays, String delayReason, EscalationLevel escalationLevel) {
    }

    @Transactional(readOnly = true)
    public List<OverdueItem> overdue(User user) {
        Access.requireRole(user, Role.HR, Role.DG);
        LocalDate today = LocalDate.now(clock);
        List<EvaluationCycle> late = cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId()).stream()
                .filter(c -> OverdueRules.overdueDays(c.getDueDate(), today, awaitingEvaluation(c)) > 0)
                .toList();
        Map<Long, String> names = UserNames.byId(users,
                late.stream().flatMap(c -> Stream.of(c.getEmployeeId(), c.getEvaluatorId())).toList());
        return late.stream()
                .map(c -> toItem(c, OverdueRules.overdueDays(c.getDueDate(), today, true), names))
                .sorted(Comparator.comparingLong(OverdueItem::overdueDays).reversed())
                .toList();
    }

    static boolean awaitingEvaluation(EvaluationCycle c) {
        return c.getStatus().isActive() && c.getEvaluatedOn() == null;
    }

    private static OverdueItem toItem(EvaluationCycle c, long days, Map<Long, String> names) {
        return new OverdueItem(c.getId(), c.getEmployeeId(), names.get(c.getEmployeeId()), c.getEvaluatorId(),
                names.get(c.getEvaluatorId()), c.getDueDate(), days, c.getDelayReason(), OverdueRules.escalationLevel(days));
    }
}
