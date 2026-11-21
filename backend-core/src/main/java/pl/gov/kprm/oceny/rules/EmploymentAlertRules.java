package pl.gov.kprm.oceny.rules;

import pl.gov.kprm.oceny.domain.EmploymentStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Skutki kadrowe kolejnych ocen okresowych — alerty dla kadr wyliczane z historii ocen (z bieżącą oceną). */
public final class EmploymentAlertRules {
    private EmploymentAlertRules() {
    }

    public enum Type {
        /** Urzędnik SC: dwie kolejne oceny negatywne — obowiązek rozwiązania stosunku pracy (art. 71 ust. 1 pkt 1). */
        TWO_NEGATIVE_TERMINATION,
        /** Pracownik SC (umowa na czas nieokreślony): dwie kolejne oceny negatywne — informacja dla kadr. */
        TWO_NEGATIVE_INFO,
        /** Urzędnik SC: dwie kolejne oceny na najwyższych poziomach — obligatoryjny kolejny stopień (art. 89 ust. 2). */
        MANDATORY_GRADE
    }

    public enum Severity { CRITICAL, INFO }

    public record Alert(Type type, Severity severity, String legalBasis, String message, LocalDate since) {
    }

    public static List<Alert> evaluate(EmploymentStatus status, Integer currentGrade, LocalDate lastGradeDate,
                                       List<HistoryEntry> history) {
        List<HistoryEntry> sorted = history.stream().sorted(Comparator.comparing(HistoryEntry::evaluatedOn)).toList();
        List<Alert> alerts = new ArrayList<>();
        if (lastTwoNegative(sorted)) {
            LocalDate since = sorted.get(sorted.size() - 1).evaluatedOn();
            if (status == EmploymentStatus.URZEDNIK_SC) {
                alerts.add(new Alert(Type.TWO_NEGATIVE_TERMINATION, Severity.CRITICAL,
                        "art. 71 ust. 1 pkt 1 ustawy o służbie cywilnej",
                        "Dwie kolejne negatywne oceny okresowe urzędnika służby cywilnej — stosunek pracy rozwiązuje się "
                                + "z mocy obowiązku ustawowego; przygotuj dokumentację kadrową", since));
            } else if (status == EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY) {
                alerts.add(new Alert(Type.TWO_NEGATIVE_INFO, Severity.INFO, "ustawa o służbie cywilnej, rozdział 7",
                        "Dwie kolejne negatywne oceny okresowe pracownika służby cywilnej — przeanalizuj dalsze "
                                + "działania kadrowe", since));
            }
        }
        if (status == EmploymentStatus.URZEDNIK_SC
                && GradeRules.mandatoryGradeEligible(true, currentGrade, lastGradeDate, sorted)) {
            alerts.add(new Alert(Type.MANDATORY_GRADE, Severity.INFO, "art. 89 ust. 2 ustawy o służbie cywilnej",
                    "Dwie kolejne oceny pozytywne na poziomie „powyżej” lub „znacznie powyżej oczekiwań” — "
                            + "obligatoryjne przyznanie kolejnego stopnia służbowego",
                    sorted.get(sorted.size() - 1).evaluatedOn()));
        }
        return alerts;
    }

    private static boolean lastTwoNegative(List<HistoryEntry> sorted) {
        int n = sorted.size();
        return n >= 2 && sorted.get(n - 1).outcome() == EvaluationOutcome.NEGATIVE
                && sorted.get(n - 2).outcome() == EvaluationOutcome.NEGATIVE;
    }
}
