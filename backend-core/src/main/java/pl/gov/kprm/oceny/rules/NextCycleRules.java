package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;

/**
 * Kolejny cykl po zakończonej ocenie: po negatywnej — ponowna ocena po 6 mies. od zapoznania (art. 81 ust. 9);
 * po pozytywnej — zwykły cykl liczony od dnia sporządzenia oceny (art. 81 ust. 4).
 */
public final class NextCycleRules {
    private NextCycleRules() {
    }

    public record Plan(LocalDate startDate, boolean reevaluation) {
    }

    public static Plan plan(EvaluationOutcome outcome, LocalDate evaluatedOn, LocalDate acknowledgedAt, LocalDate today) {
        if (outcome == EvaluationOutcome.NEGATIVE) {
            return new Plan(acknowledgedAt != null ? acknowledgedAt : today, true);
        }
        LocalDate start = evaluatedOn != null ? evaluatedOn : acknowledgedAt;
        return new Plan(start != null ? start : today, false);
    }
}
