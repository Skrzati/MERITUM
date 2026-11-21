package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Kwartalne podsumowania obserwacji (Standardy ZZL: cykliczna informacja zwrotna): przypomnienie po 90 dniach bez
 * aktywności w cyklu, pilne po 120 dniach.
 */
public final class CheckinRules {
    public static final int REMINDER_DAYS = 90;
    public static final int URGENT_DAYS = 120;

    public enum Urgency { NONE, DUE, URGENT }

    private CheckinRules() {
    }

    /** Ostatnia aktywność: najpóźniejsza z dat (rozpoczęcie, potwierdzenie kryteriów, notatka, check-in). */
    public static LocalDate lastActivity(LocalDate startDate, LocalDate... others) {
        return Stream.concat(Stream.of(startDate), Stream.of(others)).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElseThrow();
    }

    public static long daysSince(LocalDate lastActivity, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(lastActivity, today));
    }

    public static Urgency urgency(long daysSinceActivity) {
        if (daysSinceActivity >= URGENT_DAYS) return Urgency.URGENT;
        return daysSinceActivity >= REMINDER_DAYS ? Urgency.DUE : Urgency.NONE;
    }
}
