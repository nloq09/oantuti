package com.rpsbattle.tournament.registration;

/** A claimed affiliation is never treated as independently verified. */
public record RegistrationPolicy(RegistrationApprovalPolicy approvalPolicy, boolean requireAffiliationVerification) {
    public RegistrationPolicy {
        approvalPolicy = approvalPolicy == null ? RegistrationApprovalPolicy.defaultForNewTournament() : approvalPolicy;
    }
    public static RegistrationPolicy legacy() {
        return new RegistrationPolicy(RegistrationApprovalPolicy.MANUAL_REVIEW, false);
    }
}
