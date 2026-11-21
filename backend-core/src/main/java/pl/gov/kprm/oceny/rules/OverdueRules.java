package pl.gov.kprm.oceny.rules;

import pl.gov.kprm.oceny.domain.EscalationLevel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Termin oceny nie blokuje jej sporządzenia — opóźnienie jest liczone i eskalowane. */
public final class OverdueRules {
    private static final int EVALUATOR_MAX_DAYS = 7;
    private static final int DEPUTY_MAX_DAYS = 14;
    private static final int HR_MAX_DAYS = 30;

    private OverdueRules() {
    }

    /** Liczba dni po terminie (0, gdy termin nie minął lub ocena została już sporządzona). */
    public static long overdueDays(LocalDate dueDate, LocalDate today, boolean awaitingEvaluation) {
        if (!awaitingEvaluation || dueDate == null || !dueDate.isBefore(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    public static EscalationLevel escalationLevel(long overdueDays) {
        if (overdueDays <= EVALUATOR_MAX_DAYS) return EscalationLevel.OCENIAJACY;
        if (overdueDays <= DEPUTY_MAX_DAYS) return EscalationLevel.ZASTEPCA;
        if (overdueDays <= HR_MAX_DAYS) return EscalationLevel.KADRY;
        return EscalationLevel.DYREKTOR_GENERALNY;
    }
}
