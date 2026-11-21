package pl.gov.kprm.oceny.rules;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
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
    /** Art. 83 ust. 3 — sprzeciw rozpatruje się w terminie 14 dni od dnia wniesienia. */
    public static final int APPEAL_DECISION_DAYS = 14;
    /** Art. 89 ust. 2 — obligatoryjny stopień w terminie 30 dni od zapoznania z oceną. */
    public static final int MANDATORY_GRADE_DAYS = 30;
    /** § 9 ust. 1 i ust. 2 — 30 dni od zmiany stanowiska. */
    public static final int SCOPE_CHANGE_DAYS = 30;
    /**
     * Ponowne sporządzenie oceny po uwzględnieniu sprzeciwu (art. 83 ust. 4) — ustawa nie określa terminu; przyjęto
     * termin organizacyjny równy terminowi rozpatrzenia sprzeciwu, liczony od decyzji.
     */
    public static final int REDO_EVALUATION_DAYS = APPEAL_DECISION_DAYS;
    /** Art. 81 ust. 6 i ust. 8 — próg 6 miesięcy. */
    public static final int SIX_MONTHS = 6;

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

    /**
     * Art. 115 KC: termin przypadający na sobotę lub dzień ustawowo wolny od pracy (niedziela, święto) upływa
     * najbliższego dnia roboczego.
     */
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
        long extension = mergeContinuous(absences).stream()
                .filter(AbsencePeriod::isLongerThanOneMonth)
                .filter(a -> a.overlaps(start, base))
                .mapToLong(AbsencePeriod::days)
                .sum();
        return shiftToWorkingDay(base.plusDays(extension));
    }

    /**
     * Nieobecność ciągła (zasady KPRM „liczenie terminów”): kolejne okresy usprawiedliwionej nieobecności, między którymi
     * wypadają wyłącznie dni wolne od pracy (sobota, niedziela, święto), stanowią jedną nieobecność.
     */
    public List<AbsencePeriod> mergeContinuous(List<AbsencePeriod> absences) {
        List<AbsencePeriod> sorted = absences.stream().sorted(Comparator.comparing(AbsencePeriod::start)).toList();
        List<AbsencePeriod> merged = new ArrayList<>();
        for (AbsencePeriod next : sorted) {
            int last = merged.size() - 1;
            if (last >= 0 && onlyDaysOffBetween(merged.get(last).end(), next.start())) {
                AbsencePeriod current = merged.get(last);
                LocalDate end = next.end().isAfter(current.end()) ? next.end() : current.end();
                merged.set(last, new AbsencePeriod(current.start(), end));
            } else {
                merged.add(next);
            }
        }
        return merged;
    }

    private boolean onlyDaysOffBetween(LocalDate endOfFirst, LocalDate startOfNext) {
        for (LocalDate d = endOfFirst.plusDays(1); d.isBefore(startOfNext); d = d.plusDays(1)) {
            if (isWorkingDay(d)) {
                return false;
            }
        }
        return true;
    }

    public LocalDate appealDecisionDeadline(LocalDate appealedOn) {
        return appealDecisionDeadline(appealedOn, SchemaRules.DEFAULT);
    }

    public LocalDate appealDecisionDeadline(LocalDate appealedOn, SchemaRules rules) {
        return shiftToWorkingDay(appealedOn.plusDays(rules.appealDecisionDays()));
    }

    /** Termin ponownego sporządzenia oceny po decyzji REDO; nie skraca terminu, który jeszcze nie upłynął. */
    public LocalDate redoEvaluationDue(LocalDate decidedOn, LocalDate currentDue) {
        LocalDate redo = shiftToWorkingDay(decidedOn.plusDays(REDO_EVALUATION_DAYS));
        return currentDue != null && currentDue.isAfter(redo) ? currentDue : redo;
    }

    public LocalDate mandatoryGradeDeadline(LocalDate acknowledgedAt) {
        return shiftToWorkingDay(acknowledgedAt.plusDays(MANDATORY_GRADE_DAYS));
    }

    /** Ponowna ocena po negatywnej — 6 miesięcy, nieobecności NIE przedłużają terminu. */
    public LocalDate reevaluationDue(LocalDate start) {
        return reevaluationDue(start, SchemaRules.DEFAULT);
    }

    public LocalDate reevaluationDue(LocalDate start, SchemaRules rules) {
        return shiftToWorkingDay(start.plusMonths(rules.reevaluationMonths()));
    }

    public LocalDate initialInterviewDeadline(LocalDate start, boolean afterNegative) {
        return initialInterviewDeadline(start, afterNegative, SchemaRules.DEFAULT);
    }

    public LocalDate initialInterviewDeadline(LocalDate start, boolean afterNegative, SchemaRules rules) {
        int days = afterNegative ? rules.initialInterviewAfterNegativeDays() : rules.initialInterviewDays();
        return shiftToWorkingDay(start.plusDays(days));
    }

    public LocalDate appealDeadline(LocalDate acknowledgedAt) {
        return appealDeadline(acknowledgedAt, SchemaRules.DEFAULT);
    }

    public LocalDate appealDeadline(LocalDate acknowledgedAt, SchemaRules rules) {
        return shiftToWorkingDay(acknowledgedAt.plusDays(rules.appealDays()));
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
