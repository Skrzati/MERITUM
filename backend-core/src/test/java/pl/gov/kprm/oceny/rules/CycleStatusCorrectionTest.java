package pl.gov.kprm.oceny.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.gov.kprm.oceny.domain.CycleStatus.*;

/** Tryb korekty cofa wyłącznie zablokowane etapy — nigdy „do przodu” i nie z etapu sprzeciwu. */
class CycleStatusCorrectionTest {
    @Test
    void allowsOnlyBackwardCorrections() {
        assertThat(OBSERVATION.canCorrectTo(DRAFT_CRITERIA)).isTrue();
        assertThat(COMPLETED.canCorrectTo(EVALUATION_DRAFT)).isTrue();
        assertThat(COMPLETED.canCorrectTo(AWAITING_ACK)).isTrue();
        assertThat(AWAITING_ACK.canCorrectTo(EVALUATION_DRAFT)).isTrue();

        assertThat(DRAFT_CRITERIA.canCorrectTo(OBSERVATION)).isFalse();
        assertThat(OBSERVATION.canCorrectTo(COMPLETED)).isFalse();
        assertThat(APPEALED.canCorrectTo(EVALUATION_DRAFT)).as("sprzeciw rozstrzyga DG, nie korekta").isFalse();
    }
}
