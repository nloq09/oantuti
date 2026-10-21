package com.rpsbattle.tournament.registration;

/** Application review policy, independent of bot submission and check-in policies. */
public enum RegistrationApprovalPolicy {
    AUTO_APPROVE,
    MANUAL_REVIEW,
    HYBRID;

    /** Recommended when creating a tournament using the new registration pipeline. */
    public static RegistrationApprovalPolicy defaultForNewTournament() {
        return HYBRID;
    }

    /** Old snapshots have no policy: never silently auto-approve their applications. */
    public static RegistrationApprovalPolicy fromStored(RegistrationApprovalPolicy stored) {
        return stored == null ? MANUAL_REVIEW : stored;
    }
}
