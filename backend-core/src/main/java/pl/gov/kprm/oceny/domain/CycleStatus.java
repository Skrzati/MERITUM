package pl.gov.kprm.oceny.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum CycleStatus {
    DRAFT_CRITERIA, CRITERIA_CONFIRMED, OBSERVATION, EVALUATION_DRAFT, AWAITING_ACK, COMPLETED, APPEALED;

    private static final Map<CycleStatus, Set<CycleStatus>> TRANSITIONS = Map.of(
            DRAFT_CRITERIA, EnumSet.of(CRITERIA_CONFIRMED, OBSERVATION),
            CRITERIA_CONFIRMED, EnumSet.of(OBSERVATION),
            OBSERVATION, EnumSet.of(EVALUATION_DRAFT, AWAITING_ACK, DRAFT_CRITERIA), // DRAFT_CRITERIA: § 9 ust. 1
            EVALUATION_DRAFT, EnumSet.of(EVALUATION_DRAFT, AWAITING_ACK),
            AWAITING_ACK, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.of(APPEALED),
            APPEALED, EnumSet.of(COMPLETED, EVALUATION_DRAFT));

    public boolean canTransitionTo(CycleStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this != COMPLETED && this != APPEALED;
    }
}
