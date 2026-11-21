package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRuleEngineTest {
    private static final List<Criterion> CRITERIA = List.of(
            new Criterion("K08", "Rzetelność", true),
            new Criterion("K10", "Wykorzystywanie wiedzy i doskonalenie zawodowe", true),
            new Criterion("K04", "Organizacja pracy i orientacja na osiąganie celów", true),
            new Criterion("K11", "Współpraca", true),
            new Criterion("K01", "Komunikacja", true));

    private final EvaluationRuleEngine engine = new EvaluationRuleEngine();

    @Test
    void kprmCase_average42WithSingleOneIsNegative() {
        var result = engine.assess(input(scores(1, 5, 5, 5, 5), "Uzasadnienie", "Szkolenie z planowania"));

        assertThat(result.averageDisplay()).isEqualTo("4.2");
        assertThat(result.level()).isEqualTo(PerformanceLevel.MEETS);
        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.NEGATIVE);
        assertThat(result.explanation())
                .isEqualTo("Średnia 4.2 (na poziomie oczekiwań), ale kryterium Rzetelność (K08) = 1 pkt -> ocena negatywna");
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void negativeWithoutJustificationIsBlocked() {
        var result = engine.assess(input(scores(1, 5, 5, 5, 5), " ", "IPRZ"));

        assertThat(result.violations()).containsExactly("Ocena negatywna wymaga pisemnego uzasadnienia");
    }

    @Test
    void averageBelowExpectationsIsNegative() {
        var result = engine.assess(input(scores(3, 3, 5, 3, 5), "Uzasadnienie", "IPRZ"));

        assertThat(result.averageDisplay()).isEqualTo("3.8");
        assertThat(result.level()).isEqualTo(PerformanceLevel.BELOW);
        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.NEGATIVE);
    }

    @Test
    void thresholdsAreInclusiveUpperBoundsOnExactAverage() {
        assertThat(PerformanceLevel.of(8, 4)).as("2.0").isEqualTo(PerformanceLevel.SIGNIFICANTLY_BELOW);
        assertThat(PerformanceLevel.of(16, 4)).as("4.0").isEqualTo(PerformanceLevel.BELOW);
        assertThat(PerformanceLevel.of(17, 4)).as("4.25").isEqualTo(PerformanceLevel.MEETS);
        assertThat(PerformanceLevel.of(24, 4)).as("6.0").isEqualTo(PerformanceLevel.MEETS);
        assertThat(PerformanceLevel.of(32, 4)).as("8.0").isEqualTo(PerformanceLevel.ABOVE);
        assertThat(PerformanceLevel.of(33, 4)).as("8.25").isEqualTo(PerformanceLevel.SIGNIFICANTLY_ABOVE);
    }

    @Test
    void positiveEvaluationNeedsNoJustification() {
        var result = engine.assess(input(scores(5, 7, 5, 7, 5), null, "IPRZ"));

        assertThat(result.average()).isEqualByComparingTo("5.8");
        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.POSITIVE);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void iprzIsAlwaysMandatory() {
        var result = engine.assess(input(scores(7, 7, 7, 7, 7), null, null));

        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.POSITIVE);
        assertThat(result.violations()).containsExactly("Wnioski do IPRZ są obowiązkowe");
    }

    @Test
    void gradeRequestOnlyForCivilServantAndWithMotivation() {
        var in = new EvaluationInput(CRITERIA, scores(9, 9, 9, 9, 9), null, "IPRZ",
                new GradeRequest(true, ""), false, null);

        assertThat(engine.assess(in).violations()).hasSize(2);
    }

    @Test
    void gradeRequestBlockedForNegativeEvaluation() {
        var in = new EvaluationInput(CRITERIA, scores(1, 5, 5, 5, 5), "Uzasadnienie", "IPRZ",
                new GradeRequest(true, "Umotywowanie"), true, 3);

        assertThat(engine.assess(in).violations())
                .containsExactly("Wniosek o kolejny stopień służbowy można dołączyć wyłącznie do oceny pozytywnej (§ 11 ust. 5)");
    }

    @Test
    void gradeRequestBlockedAtHighestGrade() {
        var in = new EvaluationInput(CRITERIA, scores(9, 9, 9, 9, 9), null, "IPRZ",
                new GradeRequest(true, "Umotywowanie"), true, 9);

        assertThat(engine.assess(in).violations()).singleElement().asString().contains("najwyższy możliwy stopień");
    }

    @Test
    void rejectsScoresOutsideScale() {
        assertThatThrownBy(() -> engine.assess(input(scores(4, 5, 5, 5, 5), "x", "y")))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("K08");
    }

    @Test
    void rejectsMissingScore() {
        Map<String, Integer> partial = new LinkedHashMap<>(scores(5, 5, 5, 5, 5));
        partial.remove("K01");
        assertThatThrownBy(() -> engine.assess(input(partial, null, "IPRZ")))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("Brak oceny dla kryterium K01");
    }

    private static EvaluationInput input(Map<String, Integer> scores, String justification, String iprz) {
        return new EvaluationInput(CRITERIA, scores, justification, iprz, null, true, 3);
    }

    private static Map<String, Integer> scores(int... values) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(CRITERIA.get(i).code(), values[i]);
        }
        return map;
    }
}
