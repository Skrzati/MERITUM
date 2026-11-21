package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.util.Optional;

/** Art. 81 ust. 8 ustawy o SC — warunek dopuszczalności zmiany terminu oceny. */
public final class RescheduleRules {
    private RescheduleRules() {
    }

    /**
     * Zmiana terminu jest dopuszczalna, gdy od sporządzenia ostatniej oceny upłynęło więcej niż 6 miesięcy,
     * a przy ocenie po raz pierwszy — gdy okres podlegający ocenie (do nowego terminu) jest dłuższy niż 6 miesięcy.
     *
     * @return komunikat naruszenia albo pusty, gdy zmiana jest dopuszczalna
     */
    public static Optional<String> violation(boolean firstEvaluation, LocalDate periodStart, LocalDate today,
                                             LocalDate newDueDate) {
        LocalDate sixMonths = periodStart.plusMonths(DeadlineService.SIX_MONTHS);
        if (firstEvaluation && !newDueDate.isAfter(sixMonths)) {
            return Optional.of("Przy pierwszej ocenie okres podlegający ocenie musi być dłuższy niż 6 miesięcy (art. 81 ust. 8)");
        }
        if (!firstEvaluation && !today.isAfter(sixMonths)) {
            return Optional.of("Zmiana terminu możliwa, gdy od ostatniej oceny upłynęło więcej niż 6 miesięcy (art. 81 ust. 8)");
        }
        return Optional.empty();
    }
}
