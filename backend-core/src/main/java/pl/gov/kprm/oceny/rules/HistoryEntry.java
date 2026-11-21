package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;

public record HistoryEntry(EvaluationOutcome outcome, PerformanceLevel level, LocalDate evaluatedOn) {
    public boolean isTopTwoPositive() {
        return outcome == EvaluationOutcome.POSITIVE
                && (level == PerformanceLevel.ABOVE || level == PerformanceLevel.SIGNIFICANTLY_ABOVE);
    }
}
