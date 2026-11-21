package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Wyłącznie dane zagregowane — bez identyfikatorów i danych osobowych. */
@Service
@RequiredArgsConstructor
public class StatsService {
    private static final int UPCOMING_SHORT_DAYS = 7;
    private static final int UPCOMING_LONG_DAYS = 30;

    private final EvaluationCycleRepository cycles;
    private final Clock clock;

    public record Summary(long totalCycles, Map<String, Long> byStatus, Map<String, Long> byOutcome,
                          Map<String, Long> byLevel, long overdue, long dueIn7Days, long dueIn30Days,
                          long appeals) {
    }

    @Transactional(readOnly = true)
    public Summary summary(User user) {
        Access.requireRole(user, Role.DG, Role.HR);
        List<EvaluationCycle> all = cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId());
        LocalDate today = LocalDate.now(clock);
        List<EvaluationCycle> active = all.stream().filter(c -> c.getStatus().isActive()).toList();
        return new Summary(
                all.size(),
                countBy(all, c -> c.getStatus().name()),
                countBy(all, c -> c.getFinalResult() == null ? null : c.getFinalResult().name()),
                countBy(all, c -> c.getPerformanceLevel() == null ? null : c.getPerformanceLevel().name()),
                active.stream().filter(c -> c.getDueDate().isBefore(today)).count(),
                countDueWithin(active, today, UPCOMING_SHORT_DAYS),
                countDueWithin(active, today, UPCOMING_LONG_DAYS),
                all.stream().filter(c -> c.getAppealedAt() != null).count());
    }

    private static long countDueWithin(List<EvaluationCycle> active, LocalDate today, int days) {
        LocalDate limit = today.plusDays(days);
        return active.stream().filter(c -> !c.getDueDate().isBefore(today) && !c.getDueDate().isAfter(limit)).count();
    }

    private static Map<String, Long> countBy(List<EvaluationCycle> list, Function<EvaluationCycle, String> key) {
        return list.stream().map(key).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }
}
