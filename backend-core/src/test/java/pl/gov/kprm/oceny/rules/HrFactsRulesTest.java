package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HrFactsRulesTest {
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    @Test
    void countsOnlyAbsenceDaysInsidePeriod() {
        assertThat(HrFactsRules.absenceDaysWithin(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), FROM, TO)).isEqualTo(10);
        assertThat(HrFactsRules.absenceDaysWithin(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 5), FROM, TO)).isEqualTo(5);
        assertThat(HrFactsRules.absenceDaysWithin(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), FROM, TO)).isZero();
    }

    @Test
    void detectsOverloadSignalsInNotes() {
        assertThat(HrFactsRules.signalsOverload("Przepracowała 22 NADGODZINY w marcu")).isTrue();
        assertThat(HrFactsRules.signalsOverload("Prowadzi trzy projekty równolegle")).isTrue();
        assertThat(HrFactsRules.signalsOverload("Terminowo wdrożyła nowy obieg dokumentów")).isFalse();
        assertThat(HrFactsRules.signalsOverload(null)).isFalse();
    }
}
