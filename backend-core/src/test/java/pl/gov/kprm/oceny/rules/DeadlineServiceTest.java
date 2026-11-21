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
    void kprmExample_sickLeaveAndLeaveSeparatedByWeekendAreOneContinuousAbsence() {
        // „liczenie terminów” KPRM: L4 5–16.10.2026 (pn–pt) + urlop 19.10–6.11.2026 => nieobecność 5.10–6.11 (> 1 mies.)
        var sickLeave = new AbsencePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 16));
        var leave = new AbsencePeriod(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 6));

        assertThat(deadlines.mergeContinuous(List.of(leave, sickLeave)))
                .containsExactly(new AbsencePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 6)));
        // 2026-03-02 + 24 mies. = 2028-03-02 (czw.) + 33 dni = 2028-04-04 (wt.)
        assertThat(deadlines.periodicEvaluationDue(LocalDate.of(2026, 3, 2), 24, List.of(sickLeave, leave)))
                .isEqualTo(LocalDate.of(2028, 4, 4));
    }

    @Test
    void absencesSeparatedByWorkingDayAreNotMerged() {
        var first = new AbsencePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 15));
        var second = new AbsencePeriod(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 6)); // 16.10 (pt.) roboczy

        assertThat(deadlines.mergeContinuous(List.of(first, second))).hasSize(2);
    }

    @Test
    void appealDecisionDeadlineIs14Days() {
        assertThat(deadlines.appealDecisionDeadline(LocalDate.of(2026, 11, 17))).isEqualTo(LocalDate.of(2026, 12, 1));
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

    @Test
    void art115KcSundayAndHolidayDeadlineMovesToNextWorkingDay() {
        assertThat(deadlines.shiftToWorkingDay(LocalDate.of(2026, 11, 22))).as("niedziela").isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(deadlines.appealDeadline(LocalDate.of(2026, 11, 4))).as("7 dni → 11.11 (święto)")
                .isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void reevaluationAfterNegativeIgnoresLongAbsenceUnlikePeriodicEvaluation() {
        LocalDate start = LocalDate.of(2026, 3, 2);
        List<AbsencePeriod> longSickLeave = List.of(new AbsencePeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));
        assertThat(deadlines.periodicEvaluationDue(start, 6, longSickLeave)).as("art. 81 ust. 7 przedłuża")
                .isAfter(LocalDate.of(2026, 9, 2));
        assertThat(deadlines.reevaluationDue(start)).as("art. 81 ust. 9 — bez przedłużenia").isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    void reevaluationCriteriaDeadlineIsSevenDays() {
        assertThat(deadlines.initialInterviewDeadline(LocalDate.of(2026, 11, 2), true)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(deadlines.initialInterviewDeadline(LocalDate.of(2026, 11, 2), false)).isEqualTo(LocalDate.of(2026, 12, 2));
    }

    @Test
    void redoEvaluationDueNeverShortensRunningDeadline() {
        LocalDate decided = LocalDate.of(2026, 11, 20);
        assertThat(deadlines.redoEvaluationDue(decided, LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(deadlines.redoEvaluationDue(decided, LocalDate.of(2027, 1, 15))).isEqualTo(LocalDate.of(2027, 1, 15));
    }
}
