package com.rpsbattle.tournament.registration;

/** A claimed affiliation is never treated as independently verified. */
public record RegistrationPolicy(RegistrationApprovalPolicy approvalPolicy, boolean requireAffiliationVerification, WaitlistPolicy waitlistPolicy) {
    public RegistrationPolicy {
        approvalPolicy = approvalPolicy == null ? RegistrationApprovalPolicy.defaultForNewTournament() : approvalPolicy;
        waitlistPolicy = waitlistPolicy == null ? WaitlistPolicy.FIRST_COME_FIRST_SERVED : waitlistPolicy;
    }
    public RegistrationPolicy(RegistrationApprovalPolicy approvalPolicy,boolean requireAffiliationVerification) {
        this(approvalPolicy,requireAffiliationVerification,WaitlistPolicy.FIRST_COME_FIRST_SERVED);
    }
    public static RegistrationPolicy legacy() {
        return new RegistrationPolicy(RegistrationApprovalPolicy.MANUAL_REVIEW, false);
    }
}
