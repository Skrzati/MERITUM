package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineServiceTest {
    private final DeadlineService deadlines = new DeadlineService();

    @Test
    void computesEasterSunday() {
        assertThat(DeadlineService.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(DeadlineService.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void recognisesMovableAndFixedHolidays() {
        assertThat(deadlines.isPublicHoliday(LocalDate.of(2026, 4, 6))).as("Poniedziałek Wielkanocny").isTrue();
        assertThat(deadlines.isPublicHoliday(LocalDate.of(2026, 6, 4))).as("Boże Ciało").isTrue();
        assertThat(deadlines.isPublicHoliday(LocalDate.of(2026, 11, 11))).as("Święto Niepodległości").isTrue();
        assertThat(deadlines.isPublicHoliday(LocalDate.of(2026, 12, 24))).as("Wigilia od 2025").isTrue();
        assertThat(deadlines.isPublicHoliday(LocalDate.of(2024, 12, 24))).isFalse();
    }

    @Test
    void shiftsSaturdayToMonday() {
        assertThat(deadlines.shiftToWorkingDay(LocalDate.of(2026, 11, 7))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void shiftsHolidayToNextWorkingDay() {
        assertThat(deadlines.shiftToWorkingDay(LocalDate.of(2026, 11, 11))).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(deadlines.shiftToWorkingDay(LocalDate.of(2026, 6, 4))).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(deadlines.shiftToWorkingDay(LocalDate.of(2026, 12, 24))).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void absenceLongerThanMonthExtendsPeriodicEvaluation() {
        LocalDate start = LocalDate.of(2025, 3, 3);
        var absence = new AbsencePeriod(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 7, 15)); // 45 dni

        // 2027-03-03 + 45 dni = 2027-04-17 (sobota) -> poniedziałek 2027-04-19
        assertThat(deadlines.periodicEvaluationDue(start, 24, List.of(absence)))
                .isEqualTo(LocalDate.of(2027, 4, 19));
    }

    @Test
    void absenceOfOneMonthOrLessDoesNotExtend() {
        LocalDate start = LocalDate.of(2025, 3, 3);
        var exactlyOneMonth = new AbsencePeriod(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));

        assertThat(deadlines.periodicEvaluationDue(start, 24, List.of(exactlyOneMonth)))
                .isEqualTo(LocalDate.of(2027, 3, 3));
    }

    @Test
    void reevaluationAfterNegativeIsNotExtendedByAbsence() {
        assertThat(deadlines.reevaluationDue(LocalDate.of(2025, 3, 3))).isEqualTo(LocalDate.of(2025, 9, 3));
    }

    @Test
    void computesInterviewAndAppealDeadlines() {
        LocalDate start = LocalDate.of(2026, 10, 2);
        assertThat(deadlines.initialInterviewDeadline(start, false)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(deadlines.initialInterviewDeadline(start, true)).isEqualTo(LocalDate.of(2026, 10, 9));
        // zapoznanie 2026-11-04 + 7 = 2026-11-11 (święto) -> 2026-11-12
        assertThat(deadlines.appealDeadline(LocalDate.of(2026, 11, 4))).isEqualTo(LocalDate.of(2026, 11, 12));
    }
}
