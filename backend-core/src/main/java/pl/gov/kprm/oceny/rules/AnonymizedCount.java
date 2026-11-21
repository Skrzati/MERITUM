package pl.gov.kprm.oceny.rules;

/**
 * Liczność po k-anonimizacji: wartości 1..k-1 nie opuszczają serwera ({@code value = null, suppressed = true}).
 * Zero nie identyfikuje nikogo, więc jest zwracane wprost.
 */
public record AnonymizedCount(Long value, boolean suppressed) {
    public static AnonymizedCount of(long count, int k) {
        return count > 0 && count < k ? new AnonymizedCount(null, true) : new AnonymizedCount(count, false);
    }
}
