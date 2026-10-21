package com.rpsbattle.tournament.registration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Internal eligibility result; player responses must use a separate public projection. */
public record EligibilityDecision(Decision decision, List<EligibilityReasonCode> reasonCodes) {
    public enum Decision { APPROVE, REVIEW, WAITLIST, REJECT }

    public EligibilityDecision {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reasonCodes, "reasonCodes");
        // Copy before deduplication also rejects null elements and caller mutation.
        reasonCodes = List.copyOf(new LinkedHashSet<>(List.copyOf(reasonCodes)));
        if (decision == Decision.APPROVE && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Approval cannot carry unresolved eligibility reasons");
        }
        if (decision != Decision.APPROVE && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Non-approval decisions require a business reason");
        }
    }
}
