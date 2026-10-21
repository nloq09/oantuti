package com.rpsbattle.tournament.registration;

/** Stable business codes. Exception messages and private organizer notes do not belong here. */
public enum EligibilityReasonCode {
    DUPLICATE_REGISTRATION,
    MISSING_REQUIRED_FIELD,
    LANGUAGE_NOT_ALLOWED,
    AFFILIATION_NOT_VERIFIED,
    TEAM_INVALID,
    CAPACITY_FULL,
    ACCOUNT_NOT_ELIGIBLE,
    REGISTRATION_CLOSED,
    MANUAL_REVIEW_REQUIRED
}
