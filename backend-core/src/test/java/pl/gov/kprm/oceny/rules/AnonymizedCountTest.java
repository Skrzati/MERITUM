package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymizedCountTest {
    @Test
    void suppressesOneToKMinusOneButNotZeroOrK() {
        assertThat(AnonymizedCount.of(0, 5)).isEqualTo(new AnonymizedCount(0L, false));
        assertThat(AnonymizedCount.of(1, 5)).isEqualTo(new AnonymizedCount(null, true));
        assertThat(AnonymizedCount.of(4, 5)).isEqualTo(new AnonymizedCount(null, true));
        assertThat(AnonymizedCount.of(5, 5)).isEqualTo(new AnonymizedCount(5L, false));
    }
}
