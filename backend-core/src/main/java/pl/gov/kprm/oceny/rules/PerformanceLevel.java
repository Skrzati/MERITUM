package pl.gov.kprm.oceny.rules;

/** Poziomy wykonywania obowiązków wg średniej (§ 11). Górne granice są włączne. */
public enum PerformanceLevel {
    SIGNIFICANTLY_BELOW("znacznie poniżej oczekiwań", 2),
    BELOW("poniżej oczekiwań", 4),
    MEETS("na poziomie oczekiwań", 6),
    ABOVE("powyżej oczekiwań", 8),
    SIGNIFICANTLY_ABOVE("znacznie powyżej oczekiwań", 9);

    private final String label;
    private final int upperBound;

    PerformanceLevel(String label, int upperBound) {
        this.label = label;
        this.upperBound = upperBound;
    }

    /** Porównanie na liczbach całkowitych (sum <= granica * n) — dokładne, bez zaokrągleń średniej. */
    public static PerformanceLevel of(int sum, int count) {
        for (PerformanceLevel level : values()) {
            if (sum <= level.upperBound * count) {
                return level;
            }
        }
        throw new IllegalArgumentException("Średnia poza skalą");
    }

    public boolean isBelowExpectations() {
        return this == SIGNIFICANTLY_BELOW || this == BELOW;
    }

    public String label() {
        return label;
    }
}
