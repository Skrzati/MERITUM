package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.gov.kprm.oceny.rules.CheckinRules.Urgency.*;

class CheckinRulesTest {
    private static final LocalDate START = LocalDate.of(2025, 6, 2);

    @Test
    void lastActivityIsLatestOfStartConfirmationAndNote() {
        assertThat(CheckinRules.lastActivity(START, null, null)).isEqualTo(START);
        assertThat(CheckinRules.lastActivity(START, LocalDate.of(2025, 6, 12), null)).isEqualTo(LocalDate.of(2025, 6, 12));
        assertThat(CheckinRules.lastActivity(START, LocalDate.of(2025, 6, 12), LocalDate.of(2025, 7, 15)))
                .isEqualTo(LocalDate.of(2025, 7, 15));
    }

    @Test
    void remindsAfterNinetyDaysAndEscalatesAfterHundredTwenty() {
        assertThat(CheckinRules.urgency(89)).isEqualTo(NONE);
        assertThat(CheckinRules.urgency(90)).isEqualTo(DUE);
        assertThat(CheckinRules.urgency(119)).isEqualTo(DUE);
        assertThat(CheckinRules.urgency(120)).isEqualTo(URGENT);
        assertThat(CheckinRules.daysSince(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 1))).as("data z przyszłości").isZero();
    }

    @Test
    void appealAuthorityDependsOnDirectorGeneralPosition() {
        assertThat(AppealAuthority.of(true).genitive()).isEqualTo("dyrektora generalnego urzędu");
        assertThat(AppealAuthority.of(false).label()).isEqualTo("Kierownik urzędu");
    }
}
