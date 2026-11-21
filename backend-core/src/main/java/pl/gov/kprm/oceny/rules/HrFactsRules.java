package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** Twarde fakty dla HR Partnera — liczone, nie zgadywane. */
public final class HrFactsRules {
    /** Te same sygnały przeciążenia co w ai-gateway (assistants.OVERLOAD_RE). */
    private static final Pattern OVERLOAD = Pattern.compile(
            "nadgodzin|przeciąż|brak czasu|zastępstw|L4|zmęcz|równolegle|po godzinach",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private HrFactsRules() {
    }

    public static boolean signalsOverload(String note) {
        return note != null && OVERLOAD.matcher(note).find();
    }

    /** Dni kalendarzowe nieobecności (włącznie) przypadające w okresie [from, to]. */
    public static long absenceDaysWithin(LocalDate absenceStart, LocalDate absenceEnd, LocalDate from, LocalDate to) {
        LocalDate start = absenceStart.isAfter(from) ? absenceStart : from;
        LocalDate end = absenceEnd.isBefore(to) ? absenceEnd : to;
        return end.isBefore(start) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
    }
}
