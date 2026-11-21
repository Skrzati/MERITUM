package pl.gov.kprm.oceny.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum CycleStatus {
    DRAFT_CRITERIA, CRITERIA_CONFIRMED, OBSERVATION, EVALUATION_DRAFT, AWAITING_ACK, COMPLETED, APPEALED,
    /** Art. 83 ust. 5 — odwołanie do sądu pracy; cykl objęty blokadą retencji (legal hold). */
    APPEALED_TO_COURT;

    private static final Map<CycleStatus, Set<CycleStatus>> TRANSITIONS = Map.of(
            DRAFT_CRITERIA, EnumSet.of(CRITERIA_CONFIRMED, OBSERVATION),
            CRITERIA_CONFIRMED, EnumSet.of(OBSERVATION),
            OBSERVATION, EnumSet.of(EVALUATION_DRAFT, AWAITING_ACK, DRAFT_CRITERIA), // DRAFT_CRITERIA: § 9 ust. 1
            EVALUATION_DRAFT, EnumSet.of(EVALUATION_DRAFT, AWAITING_ACK),
            AWAITING_ACK, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.of(APPEALED, APPEALED_TO_COURT),
            APPEALED, EnumSet.of(COMPLETED, EVALUATION_DRAFT),
            APPEALED_TO_COURT, EnumSet.of(COMPLETED));

    /** Tryb korekty (kadry / DG / admin KPRM): cofnięcie zablokowanego etapu z uzasadnieniem, poza zwykłym przepływem. */
    private static final Map<CycleStatus, Set<CycleStatus>> CORRECTIONS = Map.of(
            OBSERVATION, EnumSet.of(DRAFT_CRITERIA),
            EVALUATION_DRAFT, EnumSet.of(OBSERVATION),
            AWAITING_ACK, EnumSet.of(EVALUATION_DRAFT),
            COMPLETED, EnumSet.of(AWAITING_ACK, EVALUATION_DRAFT));

    public boolean canCorrectTo(CycleStatus target) {
        return CORRECTIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean canTransitionTo(CycleStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this != COMPLETED && this != APPEALED && this != APPEALED_TO_COURT;
    }
}
