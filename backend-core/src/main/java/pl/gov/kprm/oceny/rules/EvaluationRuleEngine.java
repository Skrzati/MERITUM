package pl.gov.kprm.oceny.rules;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Reguły § 11: skala cząstkowa, średnia, wynik oceny i blokady formalne arkusza. */
@Component
public class EvaluationRuleEngine {
    public static final Set<Integer> ALLOWED_SCORES = Set.of(1, 3, 5, 7, 9);
    private static final int DISQUALIFYING_SCORE = 1;
    public static final int HIGHEST_GRADE = 9;

    public void validateScale(Map<String, Integer> scores) {
        List<String> violations = scaleViolations(scores);
        if (!violations.isEmpty()) {
            throw new RuleViolationException(violations);
        }
    }

    /** Wylicza wynik i zbiera naruszenia blokad. Rzuca wyjątek tylko, gdy oceny nie da się policzyć. */
    public EvaluationResult assess(EvaluationInput in) {
        return assess(in, SchemaRules.DEFAULT);
    }

    /** Progi poziomów z wersji schematu cyklu (nowelizacja) — domyślnie § 11 rozporządzenia z 2016 r. */
    public EvaluationResult assess(EvaluationInput in, SchemaRules rules) {
        List<String> scoreViolations = new ArrayList<>(scaleViolations(in.scores()));
        Set<String> codes = in.criteria().stream().map(Criterion::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            scoreViolations.add("Arkusz nie ma przypisanych kryteriów");
        }
        codes.stream().filter(code -> !in.scores().containsKey(code))
                .forEach(code -> scoreViolations.add("Brak oceny dla kryterium " + code));
        in.scores().keySet().stream().filter(code -> !codes.contains(code))
                .forEach(code -> scoreViolations.add("Ocena dla nieprzypisanego kryterium " + code));
        if (!scoreViolations.isEmpty()) {
            throw new RuleViolationException(scoreViolations);
        }

        int sum = codes.stream().mapToInt(in.scores()::get).sum();
        int count = codes.size();
        PerformanceLevel level = rules.level(sum, count);
        BigDecimal average = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
        String averageDisplay = average.setScale(1, RoundingMode.HALF_UP).toPlainString();
        List<Criterion> disqualifying = in.criteria().stream()
                .filter(c -> in.scores().get(c.code()) == DISQUALIFYING_SCORE).toList();
        EvaluationOutcome outcome = level.isBelowExpectations() || !disqualifying.isEmpty()
                ? EvaluationOutcome.NEGATIVE : EvaluationOutcome.POSITIVE;

        return new EvaluationResult(average, averageDisplay, level, outcome,
                explain(averageDisplay, level, outcome, disqualifying), formalViolations(in, outcome));
    }

    private static String explain(String avg, PerformanceLevel level, EvaluationOutcome outcome,
                                  List<Criterion> disqualifying) {
        String base = "Średnia " + avg + " (" + level.label() + ")";
        if (outcome == EvaluationOutcome.POSITIVE) {
            return base + " -> ocena pozytywna";
        }
        if (level.isBelowExpectations()) {
            return base + " -> ocena negatywna";
        }
        String names = disqualifying.stream().map(c -> c.name() + " (" + c.code() + ")")
                .collect(Collectors.joining(", "));
        String noun = disqualifying.size() == 1 ? "kryterium " : "kryteria ";
        return base + ", ale " + noun + names + " = 1 pkt -> ocena negatywna";
    }

    private static List<String> formalViolations(EvaluationInput in, EvaluationOutcome outcome) {
        List<String> violations = new ArrayList<>();
        if (outcome == EvaluationOutcome.NEGATIVE && isBlank(in.justification())) {
            violations.add("Ocena negatywna wymaga pisemnego uzasadnienia");
        }
        if (isBlank(in.iprzNotes())) {
            violations.add("Wnioski do IPRZ są obowiązkowe");
        }
        GradeRequest grade = in.gradeRequest();
        if (grade != null && grade.requested()) {
            if (!in.civilServant()) {
                violations.add("Część III (wniosek o stopień służbowy) dostępna tylko dla urzędnika służby cywilnej");
            }
            if (isBlank(grade.motivation())) {
                violations.add("Wniosek o stopień służbowy wymaga umotywowania");
            }
            if (outcome == EvaluationOutcome.NEGATIVE) {
                violations.add("Wniosek o kolejny stopień służbowy można dołączyć wyłącznie do oceny pozytywnej (§ 11 ust. 5)");
            }
            if (in.civilServiceGrade() != null && in.civilServiceGrade() >= HIGHEST_GRADE) {
                violations.add("Ocenianemu nie można przyznać kolejnego stopnia służbowego. Oceniany posiada już najwyższy możliwy stopień służbowy");
            }
        }
        return violations;
    }

    private static List<String> scaleViolations(Map<String, Integer> scores) {
        return scores.entrySet().stream()
                .filter(e -> e.getValue() == null || !ALLOWED_SCORES.contains(e.getValue()))
                .map(e -> "Kryterium " + e.getKey() + ": dozwolone oceny to 1, 3, 5, 7, 9 (podano " + e.getValue() + ")")
                .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
