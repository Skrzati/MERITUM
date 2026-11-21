package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Okres nieobecności, obie daty włącznie. */
public record AbsencePeriod(LocalDate start, LocalDate end) {
    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public boolean isLongerThanOneMonth() {
        return !end.isBefore(start.plusMonths(1));
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !start.isAfter(to) && !end.isBefore(from);
    }
}
