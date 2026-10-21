package com.rpsbattle.tournament.registration;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.rpsbattle.tournament.registration.EligibilityDecision.Decision.*;
import static com.rpsbattle.tournament.registration.EligibilityReasonCode.*;

class RegistrationEligibilityServiceTest {
    private final RegistrationEligibilityService service=new RegistrationEligibilityService();
    private RegistrationEligibilityService.Context context(RegistrationApprovalPolicy policy,boolean verify,String affiliation){
        return new RegistrationEligibilityService.Context(true,true,false,true,"CPP",Set.of("CPP"),affiliation,new RegistrationPolicy(policy,verify));
    }
    @Test void autoAndHybridApproveValidApplicationsWithoutManualIntervention(){
        for(var policy:Set.of(RegistrationApprovalPolicy.AUTO_APPROVE,RegistrationApprovalPolicy.HYBRID)){
            var result=service.evaluate(context(policy,false,null));assertThat(result.decision()).isEqualTo(APPROVE);assertThat(result.reasonCodes()).isEmpty();
        }
    }
    @Test void manualPolicyProducesAnExplicitReviewReason(){
        var result=service.evaluate(context(RegistrationApprovalPolicy.MANUAL_REVIEW,false,null));assertThat(result.decision()).isEqualTo(REVIEW);assertThat(result.reasonCodes()).containsExactly(MANUAL_REVIEW_REQUIRED);
    }
    @Test void claimedAffiliationIsNeverAutomaticallyVerified(){
        for(var policy:RegistrationApprovalPolicy.values()){
            var result=service.evaluate(context(policy,true,"UET"));assertThat(result.decision()).isEqualTo(REVIEW);assertThat(result.reasonCodes()).contains(AFFILIATION_NOT_VERIFIED);
        }
    }
    @Test void requiredAffiliationMustBeProvided(){
        var result=service.evaluate(context(RegistrationApprovalPolicy.HYBRID,true," "));assertThat(result.decision()).isEqualTo(REJECT);assertThat(result.reasonCodes()).containsExactly(MISSING_REQUIRED_FIELD);
    }
    @Test void basicFailuresOverrideApprovalAndUseOnlyStableBusinessCodes(){
        var result=service.evaluate(new RegistrationEligibilityService.Context(false,false,true,false,"RUST",Set.of("CPP"),null,new RegistrationPolicy(RegistrationApprovalPolicy.AUTO_APPROVE,false)));
        assertThat(result.decision()).isEqualTo(REJECT);assertThat(result.reasonCodes()).containsExactly(ACCOUNT_NOT_ELIGIBLE,REGISTRATION_CLOSED,DUPLICATE_REGISTRATION,MISSING_REQUIRED_FIELD,LANGUAGE_NOT_ALLOWED);
    }
    @Test void missingLanguageIsARequiredFieldFailure(){
        assertThat(service.evaluate(new RegistrationEligibilityService.Context(true,true,false,true,null,Set.of("CPP"),null,null)).reasonCodes()).containsExactly(MISSING_REQUIRED_FIELD);
    }
}
