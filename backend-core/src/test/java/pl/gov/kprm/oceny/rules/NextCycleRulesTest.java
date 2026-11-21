package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NextCycleRulesTest {
    private static final LocalDate EVALUATED = LocalDate.of(2026, 8, 3);
    private static final LocalDate ACKNOWLEDGED = LocalDate.of(2026, 8, 4);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Test
    void afterNegativeReevaluationStartsOnAcknowledgement() {
        assertThat(NextCycleRules.plan(EvaluationOutcome.NEGATIVE, EVALUATED, ACKNOWLEDGED, TODAY))
                .isEqualTo(new NextCycleRules.Plan(ACKNOWLEDGED, true));
    }

    @Test
    void afterPositiveRegularCycleStartsOnEvaluationDate() {
        assertThat(NextCycleRules.plan(EvaluationOutcome.POSITIVE, EVALUATED, ACKNOWLEDGED, TODAY))
                .isEqualTo(new NextCycleRules.Plan(EVALUATED, false));
        assertThat(NextCycleRules.plan(EvaluationOutcome.POSITIVE, null, null, TODAY).startDate()).isEqualTo(TODAY);
    }

    @Test
    void reevaluationDueIsSixMonthsAfterAcknowledgement() {
        // art. 81 ust. 9: 2026-08-04 + 6 mies. = 2027-02-04 (czwartek)
        assertThat(new DeadlineService().reevaluationDue(ACKNOWLEDGED)).isEqualTo(LocalDate.of(2027, 2, 4));
    }
}
