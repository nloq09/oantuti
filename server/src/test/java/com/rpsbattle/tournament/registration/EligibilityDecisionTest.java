package com.rpsbattle.tournament.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.rpsbattle.tournament.registration.EligibilityDecision.Decision.*;
import static com.rpsbattle.tournament.registration.EligibilityReasonCode.*;

class EligibilityDecisionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void approvedDecisionHasNoOutstandingReason() {
        assertThat(new EligibilityDecision(APPROVE, List.of()).reasonCodes()).isEmpty();
        assertThatThrownBy(() -> new EligibilityDecision(APPROVE, List.of(CAPACITY_FULL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void everyNonApprovalNeedsAnExplicitBusinessReason() {
        for (var decision : List.of(REVIEW, WAITLIST, REJECT)) {
            assertThatThrownBy(() -> new EligibilityDecision(decision, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void reviewWaitlistAndRejectionKeepTheirStableReasonCodes() {
        assertThat(new EligibilityDecision(REVIEW, List.of(AFFILIATION_NOT_VERIFIED, MANUAL_REVIEW_REQUIRED)).decision()).isEqualTo(REVIEW);
        assertThat(new EligibilityDecision(WAITLIST, List.of(CAPACITY_FULL)).reasonCodes()).containsExactly(CAPACITY_FULL);
        assertThat(new EligibilityDecision(REJECT, List.of(LANGUAGE_NOT_ALLOWED)).reasonCodes()).containsExactly(LANGUAGE_NOT_ALLOWED);
    }

    @Test void reasonsAreImmutableDeduplicatedAndKeepEvaluationOrder() {
        var source = new ArrayList<>(List.of(TEAM_INVALID, AFFILIATION_NOT_VERIFIED, TEAM_INVALID));
        var result = new EligibilityDecision(REVIEW, source);
        source.clear();
        assertThat(result.reasonCodes()).containsExactly(TEAM_INVALID, AFFILIATION_NOT_VERIFIED);
        assertThatThrownBy(() -> result.reasonCodes().add(CAPACITY_FULL)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void incompleteResultsCannotBeConstructed() {
        assertThatThrownBy(() -> new EligibilityDecision(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EligibilityDecision(REVIEW, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EligibilityDecision(REVIEW, Arrays.asList((EligibilityReasonCode) null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void internalResultSerializesOnlyDecisionAndBusinessCodes() throws Exception {
        var result = new EligibilityDecision(REVIEW, List.of(AFFILIATION_NOT_VERIFIED));
        var serialized = json.writeValueAsString(result);
        var tree = json.readTree(serialized);
        assertThat(tree.size()).isEqualTo(2);
        assertThat(tree.path("decision").asText()).isEqualTo("REVIEW");
        assertThat(tree.path("reasonCodes").get(0).asText()).isEqualTo("AFFILIATION_NOT_VERIFIED");
        assertThat(json.readValue(serialized, EligibilityDecision.class)).isEqualTo(result);
    }

    @Test void unrecognizedCodesAndInvalidResultsAreRejectedOnDeserialization() {
        for (String body : List.of(
                "{\"decision\":\"REVIEW\",\"reasonCodes\":[\"RAW_SECURITY_EXCEPTION\"]}",
                "{\"decision\":\"REVIEW\",\"reasonCodes\":[]}",
                "{\"decision\":\"APPROVE\",\"reasonCodes\":[\"CAPACITY_FULL\"]}")) {
            assertThatThrownBy(() -> json.readValue(body, EligibilityDecision.class))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
    }
}
