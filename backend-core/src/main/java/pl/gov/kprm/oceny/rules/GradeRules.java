package pl.gov.kprm.oceny.rules;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Art. 89 ust. 2 ustawy o SC — obligatoryjne przyznanie kolejnego stopnia służbowego. */
public final class GradeRules {
    private GradeRules() {
    }

    /**
     * Urzędnik SC, który od otrzymania ostatniego stopnia uzyskał dwie kolejne, następujące po sobie oceny pozytywne
     * na jednym z dwóch najwyższych poziomów, otrzymuje kolejny stopień (o ile nie ma już najwyższego).
     */
    public static boolean mandatoryGradeEligible(boolean civilServant, Integer currentGrade, LocalDate lastGradeDate,
                                                 List<HistoryEntry> history) {
        if (!civilServant || (currentGrade != null && currentGrade >= EvaluationRuleEngine.HIGHEST_GRADE)) {
            return false;
        }
        List<HistoryEntry> since = history.stream()
                .filter(h -> lastGradeDate == null || h.evaluatedOn().isAfter(lastGradeDate))
                .sorted(Comparator.comparing(HistoryEntry::evaluatedOn))
                .toList();
        for (int i = 1; i < since.size(); i++) {
            if (since.get(i - 1).isTopTwoPositive() && since.get(i).isTopTwoPositive()) {
                return true;
            }
        }
        return false;
    }
}
