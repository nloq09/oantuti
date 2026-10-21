package com.rpsbattle.tournament.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationApprovalPolicyTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void newPipelineRecommendsHybridButOldSnapshotsKeepManualReview() {
        assertThat(RegistrationApprovalPolicy.defaultForNewTournament()).isEqualTo(RegistrationApprovalPolicy.HYBRID);
        assertThat(RegistrationApprovalPolicy.fromStored(null)).isEqualTo(RegistrationApprovalPolicy.MANUAL_REVIEW);
        for (var policy : RegistrationApprovalPolicy.values()) {
            assertThat(RegistrationApprovalPolicy.fromStored(policy)).isEqualTo(policy);
        }
    }

    @Test void policyNamesRoundTripAndUnknownValuesFailClosed() throws Exception {
        for (var policy : RegistrationApprovalPolicy.values()) {
            assertThat(json.readValue(json.writeValueAsString(policy), RegistrationApprovalPolicy.class)).isEqualTo(policy);
        }
        assertThatThrownBy(() -> json.readValue("\"AUTOMATIC\"", RegistrationApprovalPolicy.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }
}
