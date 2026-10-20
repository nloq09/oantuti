package com.rpsbattle.tournament;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.rpsbattle.tournament.persistence.TournamentRepository;
import static org.assertj.core.api.Assertions.*;

class TournamentTimelineApiTest extends StageThreeWebFixture {
    @Autowired TournamentRepository repository;
    @Test void fullCreateFlowKeepsDraftAndAlignedBotPolicyUntilOwnerOpensRegistration()throws Exception{
        loginAccount();var schedule=TournamentTimelineTest.timeline();
        var t=request("/api/tournaments","POST",Map.of("name","Full timeline create","format","SINGLE_ELIMINATION","participantPolicy","BOT_ONLY","checkInRequired",true,"maxParticipants",64,"maxConcurrentMatches",2,"botPolicy",Map.of("submissionMode","BOTH","allowedLanguages",List.of("PYTHON","CPP"),"submissionOpenAt",schedule.codingOpenAt().toString(),"submissionDeadline",schedule.codingCloseAt().toString(),"deadlineBehavior","AUTO_SUBMIT_LATEST_READY","lockAtDeadline",true)),201);
        String base="/api/tournaments/"+t.path("id").asText();
        var invalid=json.valueToTree(schedule);((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("codingOpenAt",schedule.registrationOpenAt().toString());
        request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",invalid),400);
        var unchanged=request(base,"GET",null,200);assertThat(unchanged.path("status").asText()).isEqualTo("DRAFT");assertThat(unchanged.path("revision").asLong()).isEqualTo(t.path("revision").asLong());
        request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",schedule),200);
        var saved=request(base,"GET",null,200);assertThat(saved.path("status").asText()).isEqualTo("DRAFT");assertThat(saved.path("timeline").path("codingCloseAt").asText()).isEqualTo(saved.path("botPolicy").path("submissionDeadline").asText());assertThat(saved.path("participants").size()).isZero();assertThat(saved.path("registrations").size()).isZero();assertThat(saved.path("checkInRequired").asBoolean()).isTrue();
    }
    @Test void ownerConfigurationCommitsAuditAndScheduleWithRevisionAndRestoresFromDatabase()throws Exception{
        loginAccount();var t=request("/api/tournaments","POST",Map.of("name","Scheduled exam"),201);String id=t.path("id").asText(),base="/api/tournaments/"+id;
        request(base+"/timeline","PUT",Map.of("revision",999,"timeline",TournamentTimelineTest.timeline()),409);
        var saved=request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",TournamentTimelineTest.timeline()),200);
        assertThat(saved.path("phase").asText()).isEqualTo("DRAFT");assertThat(saved.path("timelineConfigured").asBoolean()).isTrue();assertThat(Instant.parse(saved.path("serverNow").asText())).isBetween(Instant.now().minusSeconds(15),Instant.now().plusSeconds(15));
        var persisted=repository.all().stream().filter(row->row.id().toString().equals(id)).findFirst().orElseThrow();assertThat(persisted.details().timeline()).isEqualTo(TournamentTimelineTest.timeline());assertThat(persisted.details().operations().audit()).anyMatch(event->event.type().equals("TIMELINE_CONFIGURED")&&event.actor().equals(account.id().value())&&event.afterValue().contains("codingCloseAt"));
        request(base+"/open-registration","POST",Map.of(),200);request(base+"/timeline","PUT",Map.of("revision",request(base,"GET",null,200).path("revision").asLong(),"timeline",TournamentTimelineTest.timeline()),400);
        var stranger=registration.register("time_"+UUID.randomUUID().toString().substring(0,8),UUID.randomUUID()+"@example.com","Timeline Test Password","Stranger");request("/api/auth/login","POST",Map.of("username",stranger.username(),"password","Timeline Test Password"),200);
        request(base+"/timeline","PUT",Map.of("revision",saved.path("revision").asLong(),"timeline",TournamentTimelineTest.timeline()),403);
    }
    @Test void publicReadIsUncachedAndDoesNotNeedAnAccountOrMutateTournament()throws Exception{
        var t=tournaments.create("Legacy phase",com.rpsbattle.tournament.domain.TournamentFormat.ROUND_ROBIN);long revision=t.revision;
        var response=accountHttp.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/tournaments/"+t.id+"/timeline")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");assertThat(json.readTree(response.body()).path("timelineConfigured").asBoolean()).isFalse();assertThat(t.revision).isEqualTo(revision);
    }
}
