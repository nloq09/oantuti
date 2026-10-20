package com.rpsbattle.worker.remote;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DistributedCapacityTest extends WorkerRegistrationTest {
    @Test void publicApiAccepts4000CodingEntrantsAndRejectsOversizedQuadraticOrLegacyTournaments()throws Exception{
        try(var browser=new Browser()){
            browser.account(false);for(String format:List.of("SINGLE_ELIMINATION","SWISS"))assertThat(browser.call("/api/tournaments","POST",Map.of("name","Capacity 4000","format",format,"maxParticipants",4000,"botPolicy",Map.of("submissionMode","CODE_IN_PLATFORM_ONLY")),null,true).status()).isEqualTo(201);
            for(var body:List.of(Map.of("name","Too large","format","SINGLE_ELIMINATION","maxParticipants",4001,"botPolicy",Map.of("submissionMode","CODE_IN_PLATFORM_ONLY")),Map.of("name","Too many pairs","format","ROUND_ROBIN","maxParticipants",4000,"botPolicy",Map.of("submissionMode","CODE_IN_PLATFORM_ONLY")),Map.of("name","Legacy bound","format","SINGLE_ELIMINATION","maxParticipants",4000)))assertThat(browser.call("/api/tournaments","POST",body,null,true).status()).isEqualTo(400);
        }
    }
}
