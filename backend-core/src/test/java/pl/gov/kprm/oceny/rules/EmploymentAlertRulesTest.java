package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.EmploymentStatus;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules.Alert;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules.Severity;
import pl.gov.kprm.oceny.rules.EmploymentAlertRules.Type;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.gov.kprm.oceny.rules.EvaluationOutcome.NEGATIVE;
import static pl.gov.kprm.oceny.rules.EvaluationOutcome.POSITIVE;

class EmploymentAlertRulesTest {
    private static final HistoryEntry NEG_2025 = new HistoryEntry(NEGATIVE, PerformanceLevel.BELOW, LocalDate.of(2025, 9, 15));
    private static final HistoryEntry NEG_2026 = new HistoryEntry(NEGATIVE, PerformanceLevel.BELOW, LocalDate.of(2026, 3, 16));
    private static final HistoryEntry POS_2024 = new HistoryEntry(POSITIVE, PerformanceLevel.MEETS, LocalDate.of(2024, 6, 1));

    @Test
    void twoConsecutiveNegativesOfCivilServantAreCriticalArt71() {
        List<Alert> alerts = EmploymentAlertRules.evaluate(EmploymentStatus.URZEDNIK_SC, 2, null,
                List.of(NEG_2026, POS_2024, NEG_2025));
        assertThat(alerts).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(Type.TWO_NEGATIVE_TERMINATION);
            assertThat(a.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(a.legalBasis()).contains("art. 71 ust. 1 pkt 1");
            assertThat(a.since()).isEqualTo(NEG_2026.evaluatedOn());
        });
    }

    @Test
    void twoConsecutiveNegativesOfPermanentEmployeeAreOnlyInformational() {
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY, null, null,
                List.of(NEG_2025, NEG_2026))).extracting(Alert::type, Alert::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Type.TWO_NEGATIVE_INFO, Severity.INFO));
    }

    @Test
    void negativeFollowedByPositiveRaisesNothing() {
        HistoryEntry pos2026 = new HistoryEntry(POSITIVE, PerformanceLevel.MEETS, LocalDate.of(2026, 9, 1));
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.URZEDNIK_SC, 2, null,
                List.of(NEG_2025, NEG_2026, pos2026))).isEmpty();
    }

    @Test
    void twoTopEvaluationsOfCivilServantRaiseMandatoryGradeAlert() {
        List<HistoryEntry> top = List.of(new HistoryEntry(POSITIVE, PerformanceLevel.ABOVE, LocalDate.of(2024, 6, 1)),
                new HistoryEntry(POSITIVE, PerformanceLevel.SIGNIFICANTLY_ABOVE, LocalDate.of(2026, 6, 1)));
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.URZEDNIK_SC, 3, null, top))
                .extracting(Alert::type).containsExactly(Type.MANDATORY_GRADE);
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY, null, null, top)).isEmpty();
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.URZEDNIK_SC, 9, null, top)).as("9. stopień").isEmpty();
    }

    @Test
    void statusesOutsidePeriodicEvaluationRaiseNoTerminationAlert() {
        assertThat(EmploymentAlertRules.evaluate(EmploymentStatus.PRACOWNIK_SC_OKRESLONY, null, null,
                List.of(NEG_2025, NEG_2026))).isEmpty();
        assertThat(EmploymentStatus.PRACOWNIK_SC_OKRESLONY.subjectToPeriodicEvaluation()).isFalse();
        assertThat(EmploymentStatus.WYZSZE_STANOWISKO_SC.subjectToPeriodicEvaluation()).isFalse();
        assertThat(EmploymentStatus.URZEDNIK_SC.isCivilServant()).isTrue();
    }
}
