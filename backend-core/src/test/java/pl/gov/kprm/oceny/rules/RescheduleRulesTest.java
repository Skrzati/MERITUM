package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RescheduleRulesTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    @Test
    void subsequentEvaluationRequiresMoreThanSixMonthsElapsed() {
        assertThat(RescheduleRules.violation(false, START, LocalDate.of(2026, 7, 15), LocalDate.of(2028, 3, 1))).isPresent();
        assertThat(RescheduleRules.violation(false, START, LocalDate.of(2026, 7, 16), LocalDate.of(2028, 3, 1))).isEmpty();
    }

    @Test
    void firstEvaluationRequiresPeriodLongerThanSixMonths() {
        assertThat(RescheduleRules.violation(true, START, START.plusDays(10), LocalDate.of(2026, 7, 15))).isPresent();
        assertThat(RescheduleRules.violation(true, START, START.plusDays(10), LocalDate.of(2026, 7, 16))).isEmpty();
    }
}
