package com.rpsbattle.tournament.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import static com.rpsbattle.tournament.registration.EligibilityDecision.Decision.*;
import static com.rpsbattle.tournament.registration.EligibilityReasonCode.*;

/** Pure evaluation. Context is built by the server, never accepted as request JSON. */
@Service
public class RegistrationEligibilityService {
    public record Context(boolean accountActive, boolean registrationOpen, boolean alreadyRegistered,
                          boolean rulesAccepted, String language, Set<String> allowedLanguages,
                          String affiliation, RegistrationPolicy policy) {}

    public EligibilityDecision evaluate(Context context) {
        var reasons = new ArrayList<EligibilityReasonCode>();
        if (!context.accountActive()) reasons.add(ACCOUNT_NOT_ELIGIBLE);
        if (!context.registrationOpen()) reasons.add(REGISTRATION_CLOSED);
        if (context.alreadyRegistered()) reasons.add(DUPLICATE_REGISTRATION);
        if (!context.rulesAccepted()) reasons.add(MISSING_REQUIRED_FIELD);
        if (context.language() == null || context.language().isBlank()) reasons.add(MISSING_REQUIRED_FIELD);
        else if (!context.allowedLanguages().contains(context.language())) reasons.add(LANGUAGE_NOT_ALLOWED);
        var policy = context.policy() == null ? RegistrationPolicy.legacy() : context.policy();
        if (policy.requireAffiliationVerification() && (context.affiliation() == null || context.affiliation().isBlank())) reasons.add(MISSING_REQUIRED_FIELD);
        if (!reasons.isEmpty()) return new EligibilityDecision(REJECT, reasons);
        // No trusted affiliation verifier exists yet: a required claim always goes to a human.
        if (policy.requireAffiliationVerification()) reasons.add(AFFILIATION_NOT_VERIFIED);
        if (policy.approvalPolicy() == RegistrationApprovalPolicy.MANUAL_REVIEW) reasons.add(MANUAL_REVIEW_REQUIRED);
        return reasons.isEmpty() ? new EligibilityDecision(APPROVE, List.of()) : new EligibilityDecision(REVIEW, reasons);
    }

    /** Called after locking the tournament row, with the current approved count from the DB. */
    public EligibilityDecision allocate(EligibilityDecision eligible,long approved,int capacity) {
        if (capacity < 1 || approved < 0) throw new IllegalArgumentException("Invalid capacity snapshot");
        if (eligible.decision() == APPROVE && approved >= capacity) {
            return new EligibilityDecision(WAITLIST,List.of(CAPACITY_FULL));
        }
        return eligible;
    }
}
