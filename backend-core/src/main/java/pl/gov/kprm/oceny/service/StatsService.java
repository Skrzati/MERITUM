package pl.gov.kprm.oceny.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.EvaluationCycle;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.domain.WellbeingSurvey;
import pl.gov.kprm.oceny.repository.EvaluationCycleRepository;
import pl.gov.kprm.oceny.repository.WellbeingSurveyRepository;
import pl.gov.kprm.oceny.rules.AnonymizedCount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Wyłącznie dane zagregowane; liczności 1..k-1 są ukrywane po stronie serwera (nie trafiają do JSON). */
@Service
public class StatsService {
    private static final int UPCOMING_SHORT_DAYS = 7;
    private static final int UPCOMING_LONG_DAYS = 30;

    private final EvaluationCycleRepository cycles;
    private final WellbeingSurveyRepository wellbeing;
    private final Clock clock;
    private final int k;

    public StatsService(EvaluationCycleRepository cycles, WellbeingSurveyRepository wellbeing, Clock clock,
                        @Value("${app.stats.k-anonymity:5}") int k) {
        this.cycles = cycles;
        this.wellbeing = wellbeing;
        this.clock = clock;
        this.k = Math.max(2, k);
    }

    public record Summary(int kAnonymity, AnonymizedCount totalCycles, Map<String, AnonymizedCount> byStatus,
                          Map<String, AnonymizedCount> byOutcome, Map<String, AnonymizedCount> byLevel,
                          AnonymizedCount overdue, AnonymizedCount dueIn7Days, AnonymizedCount dueIn30Days,
                          AnonymizedCount appeals, Wellbeing wellbeing) {
    }

    /** Średnie (skala 1–5) podawane tylko, gdy liczba odpowiedzi osiąga próg k. */
    public record Wellbeing(AnonymizedCount responses, BigDecimal avgWorkload, BigDecimal avgEnergy,
                            BigDecimal avgSupport, AnonymizedCount wantsConversation) {
    }

    @Transactional(readOnly = true)
    public Summary summary(User user) {
        Access.requireRole(user, Role.DG, Role.HR);
        List<EvaluationCycle> all = cycles.findByOfficeIdOrderByIdAsc(user.getOfficeId());
        LocalDate today = LocalDate.now(clock);
        List<EvaluationCycle> active = all.stream().filter(c -> c.getStatus().isActive()).toList();
        return new Summary(
                k,
                count(all.size()),
                countBy(all, c -> c.getStatus().name()),
                countBy(all, c -> c.getFinalResult() == null ? null : c.getFinalResult().name()),
                countBy(all, c -> c.getPerformanceLevel() == null ? null : c.getPerformanceLevel().name()),
                count(all.stream().filter(c -> OverdueService.awaitingEvaluation(c) && c.getDueDate().isBefore(today)).count()),
                count(dueWithin(active, today, UPCOMING_SHORT_DAYS)),
                count(dueWithin(active, today, UPCOMING_LONG_DAYS)),
                count(all.stream().filter(c -> c.getAppealedAt() != null).count()),
                wellbeing(wellbeing.findByOfficeId(user.getOfficeId())));
    }

    private Wellbeing wellbeing(List<WellbeingSurvey> responses) {
        boolean reportable = responses.size() >= k;
        return new Wellbeing(count(responses.size()),
                reportable ? avg(responses, WellbeingSurvey::getWorkload) : null,
                reportable ? avg(responses, WellbeingSurvey::getEnergy) : null,
                reportable ? avg(responses, WellbeingSurvey::getSupport) : null,
                count(responses.stream().filter(WellbeingSurvey::isWantsConversation).count()));
    }

    private AnonymizedCount count(long n) {
        return AnonymizedCount.of(n, k);
    }

    private static long dueWithin(List<EvaluationCycle> active, LocalDate today, int days) {
        LocalDate limit = today.plusDays(days);
        return active.stream().filter(c -> !c.getDueDate().isBefore(today) && !c.getDueDate().isAfter(limit)).count();
    }

    private Map<String, AnonymizedCount> countBy(List<EvaluationCycle> list, Function<EvaluationCycle, String> key) {
        Map<String, Long> raw = list.stream().map(key).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        Map<String, AnonymizedCount> result = new TreeMap<>();
        raw.forEach((name, n) -> result.put(name, count(n)));
        return result;
    }

    private static BigDecimal avg(List<WellbeingSurvey> responses, ToIntFunction<WellbeingSurvey> field) {
        int sum = responses.stream().mapToInt(field).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(responses.size()), 1, RoundingMode.HALF_UP);
    }
}
