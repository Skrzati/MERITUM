package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.gov.kprm.oceny.domain.EscalationLevel.*;

class OverdueRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Test
    void escalatesByDaysOverdue() {
        assertThat(OverdueRules.escalationLevel(1)).isEqualTo(OCENIAJACY);
        assertThat(OverdueRules.escalationLevel(7)).isEqualTo(OCENIAJACY);
        assertThat(OverdueRules.escalationLevel(8)).isEqualTo(ZASTEPCA);
        assertThat(OverdueRules.escalationLevel(14)).isEqualTo(ZASTEPCA);
        assertThat(OverdueRules.escalationLevel(15)).isEqualTo(KADRY);
        assertThat(OverdueRules.escalationLevel(30)).isEqualTo(KADRY);
        assertThat(OverdueRules.escalationLevel(31)).isEqualTo(DYREKTOR_GENERALNY);
    }

    @Test
    void countsDaysOnlyWhileEvaluationIsPending() {
        assertThat(OverdueRules.overdueDays(LocalDate.of(2026, 11, 10), TODAY, true)).isEqualTo(11);
        assertThat(OverdueRules.overdueDays(LocalDate.of(2026, 10, 15), TODAY, true)).isEqualTo(37);
        assertThat(OverdueRules.overdueDays(TODAY, TODAY, true)).isZero();
        assertThat(OverdueRules.overdueDays(LocalDate.of(2026, 10, 15), TODAY, false)).as("ocena sporządzona").isZero();
    }
}
