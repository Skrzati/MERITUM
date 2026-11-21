package pl.gov.kprm.oceny.rules;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param average        dokładna średnia (do porównań z progami)
 * @param averageDisplay średnia z 1 miejscem po przecinku (dla UI)
 * @param violations     naruszone blokady formalne; pusta lista = ocenę można zatwierdzić
 */
public record EvaluationResult(BigDecimal average, String averageDisplay, PerformanceLevel level,
                               EvaluationOutcome outcome, String explanation, List<String> violations) {
    public boolean isValid() {
        return violations.isEmpty();
    }
}
