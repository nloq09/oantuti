package com.rpsbattle.tournament;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.rpsbattle.tournament.persistence.TournamentRepository;
import static org.assertj.core.api.Assertions.*;

class TournamentTimelineApiTest extends StageThreeWebFixture {
    @Autowired TournamentRepository repository;
    @Autowired OwnedTournamentService ownedTournaments;
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
    @Test void legacyPublicationMetadataNeverDelaysCompletedResultsAcrossPublicReadModels()throws Exception{
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Immediate results compatibility","participantPolicy","HUMAN_ONLY"),201);
        String base="/api/tournaments/"+created.path("id").asText();var t=tournaments.find(created.path("id").asText());
        var a=new com.rpsbattle.tournament.domain.TournamentParticipant(UUID.randomUUID().toString(),null,null,"Winner",1,account.id().value());
        var runner=registration.register("result_"+UUID.randomUUID().toString().substring(0,8),UUID.randomUUID()+"@example.invalid","Tournament Test Password","Runner");
        var b=new com.rpsbattle.tournament.domain.TournamentParticipant(UUID.randomUUID().toString(),null,null,"Runner",2,runner.id().value());
        var match=new com.rpsbattle.tournament.domain.TournamentMatch(UUID.randomUUID().toString(),t.id,a,b);
        var schedule=TournamentTimelineTest.timeline();var future=Instant.now().plusSeconds(86400);
        ownedTournaments.submissionMutation(t,com.rpsbattle.auth.AuthService.sessionAuthentication(account),()->{
            t.timeline=new com.rpsbattle.tournament.domain.TournamentTimeline(schedule.registrationOpenAt(),schedule.registrationCloseAt(),schedule.codingOpenAt(),schedule.codingCloseAt(),schedule.validationStartAt(),schedule.validationDeadline(),schedule.checkInOpenAt(),schedule.checkInCloseAt(),schedule.tournamentStartAt(),schedule.estimatedEndAt(),future);
            t.participants.addAll(List.of(a,b));t.matches.add(match);t.status=com.rpsbattle.tournament.domain.TournamentStatus.RUNNING;match.status=com.rpsbattle.tournament.domain.TournamentMatch.Status.RUNNING;tournaments.changed(t);
        });
        assertThat(request(base+"/summary","GET",null,200).path("champion").isNull()).isTrue();
        ownedTournaments.submissionMutation(t,com.rpsbattle.auth.AuthService.sessionAuthentication(account),()->{
            match.status=com.rpsbattle.tournament.domain.TournamentMatch.Status.FINISHED;match.winnerParticipantId=a.participantId();match.finishedAt=Instant.now();t.status=com.rpsbattle.tournament.domain.TournamentStatus.FINISHED;t.finishedAt=match.finishedAt;tournaments.changed(t);
        });
        request("/api/auth/logout","POST",Map.of(),200);
        for(String suffix:List.of("","/summary","/report")){
            var view=request(base+suffix,"GET",null,200);
            assertThat(view.path("champion").path("participantId").asText()).isEqualTo(a.participantId());
        }
        var summary=request(base+"/summary","GET",null,200);assertThat(summary.path("timeline").path("resultPublishAt").asText()).isEqualTo(future.toString());
        assertThat(summary.path("ranking").get(0).path("wins").asInt()).isEqualTo(1);
        request(base+"/me","GET",null,401);request(base+"/organizer-metrics","GET",null,401);
    }

    @Test void existingDraftCanRecoverAndEditFullTimelineWithoutRecreatingTournament()throws Exception{
        loginAccount();var draft=request("/api/tournaments","POST",Map.of("name","Recover timeline draft"),201);String base="/api/tournaments/"+draft.path("id").asText();var schedule=TournamentTimelineTest.timeline();request(base+"/timeline","PUT",Map.of("revision",draft.path("revision").asLong(),"timeline",schedule),200);var t=request(base,"GET",null,200);assertThat(t.path("status").asText()).isEqualTo("DRAFT");assertThat(t.path("botPolicy").path("submissionOpenAt").asText()).isEqualTo(schedule.codingOpenAt().toString());
        var edited=TournamentTimelineTest.from(new long[]{0,60,120,240,250,270,300,330,360,420,480});request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",edited),200);var saved=request(base,"GET",null,200);assertThat(saved.path("id").asText()).isEqualTo(draft.path("id").asText());assertThat(saved.path("botPolicy").path("submissionDeadline").asText()).isEqualTo(edited.codingCloseAt().toString());assertThat(saved.path("participants").size()).isZero();assertThat(saved.path("registrations").size()).isZero();
    }
}
