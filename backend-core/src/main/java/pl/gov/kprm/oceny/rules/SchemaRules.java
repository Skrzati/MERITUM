package pl.gov.kprm.oceny.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminy i progi poziomów z wersji schematu („prawo jako kod” pod nowelizację): opcjonalne sekcje
 * {@code config.deadlines} i {@code config.levelThresholds}. Brak sekcji = wartości z rozporządzenia z 2016 r.
 */
public record SchemaRules(int initialInterviewDays, int initialInterviewAfterNegativeDays, int appealDays,
                          int appealDecisionDays, int reevaluationMonths, double[] levelThresholds) {
    public static final double[] DEFAULT_THRESHOLDS = {2, 4, 6, 8, 9};
    public static final SchemaRules DEFAULT = new SchemaRules(DeadlineService.INITIAL_INTERVIEW_DAYS,
            DeadlineService.INITIAL_INTERVIEW_AFTER_NEGATIVE_DAYS, DeadlineService.APPEAL_DAYS,
            DeadlineService.APPEAL_DECISION_DAYS, DeadlineService.REEVALUATION_MONTHS, DEFAULT_THRESHOLDS);
    private static final int MAX_DAYS = 366;
    private static final int MAX_MONTHS = 60;
    private static final double MAX_SCORE = 9;

    /** Odczyt z pełnym fallbackiem: brakujące lub błędne pola przyjmują wartości domyślne. */
    public static SchemaRules from(JsonNode config) {
        if (config == null) {
            return DEFAULT;
        }
        JsonNode d = config.path("deadlines");
        double[] thresholds = thresholds(config.path("levelThresholds"));
        return new SchemaRules(
                positive(d.path("initialInterviewDays"), DEFAULT.initialInterviewDays, MAX_DAYS),
                positive(d.path("initialInterviewAfterNegativeDays"), DEFAULT.initialInterviewAfterNegativeDays, MAX_DAYS),
                positive(d.path("appealDays"), DEFAULT.appealDays, MAX_DAYS),
                positive(d.path("appealDecisionDays"), DEFAULT.appealDecisionDays, MAX_DAYS),
                positive(d.path("reevaluationMonths"), DEFAULT.reevaluationMonths, MAX_MONTHS),
                thresholds != null ? thresholds : DEFAULT_THRESHOLDS);
    }

    /** Walidacja nowej wersji schematu — jawnie podane, ale błędne wartości są odrzucane (422). */
    public static List<String> violations(JsonNode config) {
        List<String> out = new ArrayList<>();
        JsonNode d = config == null ? null : config.path("deadlines");
        if (d != null && !d.isMissingNode() && !d.isNull()) {
            for (String f : List.of("initialInterviewDays", "initialInterviewAfterNegativeDays", "appealDays", "appealDecisionDays")) {
                if (d.has(f) && positive(d.get(f), -1, MAX_DAYS) < 0) out.add("deadlines." + f + ": liczba dni 1–" + MAX_DAYS);
            }
            if (d.has("reevaluationMonths") && positive(d.get("reevaluationMonths"), -1, MAX_MONTHS) < 0) {
                out.add("deadlines.reevaluationMonths: liczba miesięcy 1–" + MAX_MONTHS);
            }
        }
        JsonNode t = config == null ? null : config.path("levelThresholds");
        if (t != null && !t.isMissingNode() && !t.isNull() && thresholds(t) == null) {
            out.add("levelThresholds: 5 rosnących progów w skali 1–9, ostatni = 9");
        }
        return out;
    }

    /** Poziom wg progów — porównanie {@code sum <= próg * n} (bez zaokrągleń średniej). */
    public PerformanceLevel level(int sum, int count) {
        PerformanceLevel[] levels = PerformanceLevel.values();
        for (int i = 0; i < levels.length; i++) {
            if (sum <= levelThresholds[i] * count + 1e-9) {
                return levels[i];
            }
        }
        throw new IllegalArgumentException("Średnia poza skalą");
    }

    private static int positive(JsonNode n, int fallback, int max) {
        return n != null && n.canConvertToInt() && n.asInt() >= 1 && n.asInt() <= max && n.isIntegralNumber() ? n.asInt() : fallback;
    }

    private static double[] thresholds(JsonNode t) {
        if (t == null || !t.isArray() || t.size() != PerformanceLevel.values().length) {
            return null;
        }
        double[] out = new double[t.size()];
        for (int i = 0; i < out.length; i++) {
            if (!t.get(i).isNumber()) return null;
            out[i] = t.get(i).asDouble();
            if (out[i] < 1 || out[i] > MAX_SCORE || (i > 0 && out[i] <= out[i - 1])) return null;
        }
        return out[out.length - 1] == MAX_SCORE ? out : null;
    }
}
