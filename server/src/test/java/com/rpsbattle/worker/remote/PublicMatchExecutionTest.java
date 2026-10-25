package com.rpsbattle.worker.remote;

import com.rpsbattle.tournament.RemoteExecutionReadService;
import com.rpsbattle.tournament.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicMatchExecutionTest extends MatchMonitorTest {
 // These read-model fixtures contain synthetic room IDs; live execution is covered separately.
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.rpsbattle.tournament.execution.TournamentExecutionService execution;
 void safeRow(JsonNode row,String state){
  assertThat(row.path("remote").asBoolean()).isTrue();assertThat(row.path("executionState").asText()).isEqualTo(state);assertThat(row.path("onlineMatchId").isNull()).isTrue();
  for(String field:List.of("execution","executionId","jobId","workerDisplayName","workerStatus","leaseUntil","artifactToken","spec_json"))assertThat(row.has(field)).as(field).isFalse();
 }
 @Test void publicPagesTrackRemoteStartupGameplayAndReceiptWithoutRevisionOrPrivateWorkerData()throws Exception{
  try(var owner=new Browser();var guest=new Browser()){
   var f=fixture(owner.account(false));var t=f.tournament();readFixtures.add(t.id);var m=t.matches.getFirst();m.onlineMatchId="stale-room";
   t.bracket.add(new BracketNode("node",1,0,m.id,m.participantA.participantId(),m.participantB.participantId(),null,null,null,null,null));
   t.teams=new TeamState(List.of(),List.of(new TeamState.Fixture("fixture",1,"a","b",List.of(),List.of(),true,List.of(m.seriesId))));
   String base="/api/tournaments/"+t.id;long revision=t.revision;
   for(String suffix:List.of("","/summary","/bracket","/fixtures/fixture/matches","/matches")){
    var reply=guest.call(base+suffix,"GET",null,null,false);assertThat(reply.status()).as(suffix).isEqualTo(200);var body=reply.body();assertThat(body.path("revision").asLong()).isEqualTo(revision);
    safeRow(body.path(suffix.equals("/matches")?"items":"matches").get(0),"STARTING");
    assertThat(body.toString()).doesNotContain(f.machine().enrolled().workerCredential(),f.job().executionId().toString(),f.job().jobId().toString(),"private-fixture");
   }
   queue.gameplay(f.machine().id(),f.job().jobId(),new RemoteMatchQueue.Start(f.job().executionId(),f.machine().epoch()));
   var running=guest.call(base+"/summary","GET",null,null,false).body();safeRow(running.path("matches").get(0),"RUNNING_REMOTE");assertThat(running.path("revision").asLong()).isEqualTo(revision);
   results.accept(f.machine().id(),f.job().jobId(),f.upload());var pending=guest.call(base+"/matches","GET",null,null,false).body();safeRow(pending.path("items").get(0),"RESULT_PENDING");assertThat(pending.path("items").get(0).path("historyMatchId").isNull()).isTrue();assertThat(pending.path("revision").asLong()).isEqualTo(revision);
   projector.project(f.job().executionId());var done=guest.call(base+"/matches","GET",null,null,false).body();safeRow(done.path("items").get(0),"FINISHED");assertThat(done.path("items").get(0).path("historyMatchId").asText()).isEqualTo(m.id);
   t.status=TournamentStatus.CANCELLED;safeRow(guest.call(base+"/matches","GET",null,null,false).body().path("items").get(0),"FINISHED");
   assertThat(RemoteExecutionReadService.state("CANCELLED","FINISHED","DONE","DONE",true,"APPLIED",false,true,false,1)).isEqualTo("FINISHED");
  }
 }
 @Test void publicSummaryIsBoundedAndKeepsLocalRoomsSeparateFromRemoteJobs()throws Exception{
  try(var owner=new Browser();var guest=new Browser()){
   var f=fixture(owner.account(false));var t=f.tournament();readFixtures.add(t.id);var original=t.matches.getFirst();
   for(int i=0;i<22;i++){var m=new TournamentMatch(UUID.randomUUID().toString(),t.id,original.participantA,original.participantB);m.status=TournamentMatch.Status.RUNNING;m.onlineMatchId="local-"+i;t.matches.add(m);}
   var summary=guest.call("/api/tournaments/"+t.id+"/summary","GET",null,null,false).body();assertThat(summary.path("matches").size()).isEqualTo(20);assertThat(summary.path("activeMatchesTruncated").asBoolean()).isTrue();
   for(var row:summary.path("matches")){assertThat(row.path("remote").asBoolean()).isFalse();assertThat(row.path("executionState").asText()).isEqualTo("RUNNING_LOCAL");assertThat(row.path("onlineMatchId").asText()).startsWith("local-");}
   assertThat(guest.call("/api/tournaments/"+t.id+"/matches?size=50","GET",null,null,false).body().path("total").asInt()).isEqualTo(23);
  }
 }
 @Test void publicQueueLeaseAndRetryRemainVisibleWithoutExposingTheirAttemptHistory()throws Exception{
  try(var owner=new Browser();var guest=new Browser()){
   UUID actor=owner.account(false);var spec=spec(actor,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(spec.tournamentId().toString(),"Public queued fixture",TournamentFormat.ROUND_ROBIN,java.time.Instant.now());t.ownerUserId=actor;t.status=TournamentStatus.PAUSED;
   var row=db.queryForList("select * from tournament_matches where id=?",spec.matchId()).getFirst();for(String key:List.of("participant_a_id","participant_b_id")){var p=db.queryForList("select * from tournament_participants where id=?",row.get(key)).getFirst();t.participants.add(new TournamentParticipant(p.get("id").toString(),p.get("bot_id").toString(),"","Fixture",1,actor));}
   t.matches.add(new TournamentMatch(spec.matchId().toString(),t.id,t.participants.get(0),t.participants.get(1)));registry.register(t);readFixtures.add(t.id);queue.enqueue(spec);String path="/api/tournaments/"+t.id+"/summary";
   safeRow(guest.call(path,"GET",null,null,false).body().path("matches").get(0),"QUEUED");var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var job=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();safeRow(guest.call(path,"GET",null,null,false).body().path("matches").get(0),"ASSIGNED");
   db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),job.executionId());safeRow(guest.call(path,"GET",null,null,false).body().path("matches").get(0),"RECOVERING");
   db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(60)),job.executionId());queue.decline(machine.id(),job.jobId(),new RemoteMatchQueue.Decline(job.executionId(),"ARTIFACT_UNAVAILABLE"));safeRow(guest.call(path,"GET",null,null,false).body().path("matches").get(0),"RETRYING");
  }
 }
}
