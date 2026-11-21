package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.gov.kprm.oceny.rules.EvaluationOutcome.NEGATIVE;
import static pl.gov.kprm.oceny.rules.EvaluationOutcome.POSITIVE;
import static pl.gov.kprm.oceny.rules.PerformanceLevel.*;

class GradeRulesTest {
    private static final LocalDate LAST_GRADE = LocalDate.of(2021, 5, 10);

    @Test
    void twoConsecutiveTopTwoEvaluationsSinceLastGradeAreEligible() {
        var history = List.of(entry(POSITIVE, ABOVE, 2024, 6), entry(POSITIVE, SIGNIFICANTLY_ABOVE, 2026, 6));
        assertThat(GradeRules.mandatoryGradeEligible(true, 3, LAST_GRADE, history)).isTrue();
    }

    @Test
    void notEligibleWhenSequenceBrokenByLowerLevel() {
        var history = List.of(entry(POSITIVE, ABOVE, 2022, 6), entry(POSITIVE, MEETS, 2024, 6), entry(POSITIVE, ABOVE, 2026, 6));
        assertThat(GradeRules.mandatoryGradeEligible(true, 3, LAST_GRADE, history)).isFalse();
    }

    @Test
    void evaluationsBeforeLastGradeDoNotCount() {
        var history = List.of(entry(POSITIVE, ABOVE, 2020, 6), entry(POSITIVE, ABOVE, 2026, 6));
        assertThat(GradeRules.mandatoryGradeEligible(true, 3, LAST_GRADE, history)).isFalse();
    }

    @Test
    void notForEmployeesAtHighestGradeOrNonCivilServants() {
        var history = List.of(entry(POSITIVE, ABOVE, 2024, 6), entry(POSITIVE, ABOVE, 2026, 6));
        assertThat(GradeRules.mandatoryGradeEligible(true, 9, LAST_GRADE, history)).isFalse();
        assertThat(GradeRules.mandatoryGradeEligible(false, null, null, history)).isFalse();
        assertThat(GradeRules.mandatoryGradeEligible(true, 3, LAST_GRADE,
                List.of(entry(NEGATIVE, ABOVE, 2024, 6), entry(POSITIVE, ABOVE, 2026, 6)))).isFalse();
    }

    private static HistoryEntry entry(EvaluationOutcome outcome, PerformanceLevel level, int year, int month) {
        return new HistoryEntry(outcome, level, LocalDate.of(year, month, 15));
    }
}
