package pl.gov.kprm.oceny.rules;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Terminy procesu oceny z uwzględnieniem dni ustawowo wolnych w Polsce. */
@Component
public class DeadlineService {
    public static final int REEVALUATION_MONTHS = 6;
    public static final int INITIAL_INTERVIEW_DAYS = 30;
    public static final int INITIAL_INTERVIEW_AFTER_NEGATIVE_DAYS = 7;
    public static final int APPEAL_DAYS = 7;

    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1), MonthDay.of(1, 6), MonthDay.of(5, 1), MonthDay.of(5, 3), MonthDay.of(8, 15),
            MonthDay.of(11, 1), MonthDay.of(11, 11), MonthDay.of(12, 25), MonthDay.of(12, 26));
    private static final MonthDay CHRISTMAS_EVE = MonthDay.of(12, 24);
    private static final int CHRISTMAS_EVE_FREE_SINCE = 2025;

    public boolean isPublicHoliday(LocalDate date) {
        MonthDay md = MonthDay.from(date);
        return FIXED_HOLIDAYS.contains(md)
                || (md.equals(CHRISTMAS_EVE) && date.getYear() >= CHRISTMAS_EVE_FREE_SINCE)
                || movableHolidays(date.getYear()).contains(date);
    }

    public boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !isPublicHoliday(date);
    }

    /** Termin przypadający na sobotę lub dzień wolny przesuwa się na najbliższy dzień roboczy. */
    public LocalDate shiftToWorkingDay(LocalDate date) {
        LocalDate d = date;
        while (!isWorkingDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Ocena okresowa: art. 81 ust. 7 — ciągła nieobecność > 1 miesiąca przedłuża termin o czas nieobecności. */
    public LocalDate periodicEvaluationDue(LocalDate start, int periodMonths, List<AbsencePeriod> absences) {
        LocalDate base = start.plusMonths(periodMonths);
        long extension = absences.stream()
                .filter(AbsencePeriod::isLongerThanOneMonth)
                .filter(a -> a.overlaps(start, base))
                .mapToLong(AbsencePeriod::days)
                .sum();
        return shiftToWorkingDay(base.plusDays(extension));
    }

    /** Ponowna ocena po negatywnej — 6 miesięcy, nieobecności NIE przedłużają terminu. */
    public LocalDate reevaluationDue(LocalDate start) {
        return shiftToWorkingDay(start.plusMonths(REEVALUATION_MONTHS));
    }

    public LocalDate initialInterviewDeadline(LocalDate start, boolean afterNegative) {
        int days = afterNegative ? INITIAL_INTERVIEW_AFTER_NEGATIVE_DAYS : INITIAL_INTERVIEW_DAYS;
        return shiftToWorkingDay(start.plusDays(days));
    }

    public LocalDate appealDeadline(LocalDate acknowledgedAt) {
        return shiftToWorkingDay(acknowledgedAt.plusDays(APPEAL_DAYS));
    }

    static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private static Set<LocalDate> movableHolidays(int year) {
        LocalDate easter = easterSunday(year);
        Set<LocalDate> days = new HashSet<>();
        days.add(easter);
        days.add(easter.plusDays(1));   // Poniedziałek Wielkanocny
        days.add(easter.plusDays(49));  // Zielone Świątki
        days.add(easter.plusDays(60));  // Boże Ciało
        return days;
    }
}
