package com.rpsbattle.worker.remote;
import com.rpsbattle.tournament.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

// Read fixtures intentionally differ from persisted state. Keep background checkpointing from saving them.
@org.springframework.boot.test.context.SpringBootTest(webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.result-project-millis=3600000","rps.tournament.flush-millis=3600000"})
class MatchMonitorTest extends RemoteExecutionReadTest {
 String monitor(Fixture f){return "/api/tournaments/"+f.tournament().id+"/match-monitor";}
 @Test void playerOnlySeesOwnedMatchesWhileMonitorAndAttemptHistoryRequireManager()throws Exception{
  try(var owner=new Browser();var other=new Browser();var guest=new Browser();var admin=new Browser()){
   UUID actor=owner.account(false);other.account(false);admin.account(true);var f=fixture(actor);readFixtures.add(f.tournament().id);String base=monitor(f),mine=base.replace("match-monitor","my-matches");assertThat(admin.call(base,"GET",null,null,false).status()).isEqualTo(200);assertThat(admin.call(base+"/"+f.job().match().matchId()+"/attempts","GET",null,null,false).status()).isEqualTo(200);assertThat(admin.call(mine,"GET",null,null,false).body().path("total").asInt()).isZero();
   var reply=owner.call(mine,"GET",null,null,false);assertThat(reply.status()).isEqualTo(200);assertThat(reply.headers().firstValue("Cache-Control").orElse("")).contains("no-store");var row=reply.body().path("items").get(0);assertThat(row.path("executionState").asText()).isEqualTo("STARTING");assertThat(row.path("participantA").path("playerName").asText()).isNotBlank();assertThat(row.path("participantA").path("botName").asText()).isEqualTo("Remote fixture");assertThat(reply.body().toString()).doesNotContain("private-fixture","botToken","credential","sourcePath","replay_json");
   assertThat(other.call(mine,"GET",null,null,false).body().path("total").asInt()).isZero();assertThat(other.call(base,"GET",null,null,false).status()).isEqualTo(403);assertThat(guest.call(mine,"GET",null,null,false).status()).isEqualTo(401);assertThat(other.call(base+"/"+f.job().match().matchId()+"/attempts","GET",null,null,false).status()).isEqualTo(403);assertThat(owner.call(base+"/"+UUID.randomUUID()+"/attempts","GET",null,null,false).status()).isEqualTo(404);
  }
 }
 @Test void gameplayAndPendingResultUpdateWithoutTournamentRevisionOrInventedLiveRoom()throws Exception{
  try(var owner=new Browser()){
   var f=fixture(owner.account(false));readFixtures.add(f.tournament().id);String path=monitor(f);long revision=f.tournament().revision;f.tournament().matches.getFirst().onlineMatchId="stale-live-room";
   queue.gameplay(f.machine().id(),f.job().jobId(),new RemoteMatchQueue.Start(f.job().executionId(),f.machine().epoch()));var running=owner.call(path+"?status=RUNNING","GET",null,null,false).body();assertThat(running.path("revision").asLong()).isEqualTo(revision);assertThat(running.path("items").get(0).path("executionState").asText()).isEqualTo("RUNNING_REMOTE");assertThat(running.path("items").get(0).path("onlineMatchId").isNull()).isTrue();assertThat(running.path("items").get(0).path("status").asText()).isEqualTo("PENDING");
   results.accept(f.machine().id(),f.job().jobId(),f.upload());var pending=owner.call(path+"?status=RESULT_PENDING","GET",null,null,false).body();assertThat(pending.path("total").asInt()).isEqualTo(1);assertThat(pending.path("items").get(0).path("winnerParticipantId").isNull()).isTrue();projector.project(f.job().executionId());assertThat(owner.call(path+"?status=FINISHED","GET",null,null,false).body().path("items").get(0).path("historyMatchId").asText()).isEqualTo(f.job().match().matchId().toString());
   var history=owner.call(path+"/"+f.job().match().matchId()+"/attempts","GET",null,null,false);assertThat(history.status()).isEqualTo(200);assertThat(history.body().size()).isEqualTo(1);assertThat(history.body().get(0).path("gameplay_started_at").isNull()).isFalse();assertThat(history.body().toString()).doesNotContain("credential","spec_json","source_path","artifactToken");
  }
 }
 @Test void filtersApplyBeforePaginationAndUnrelatedPlayerGamesNeverEnterTheResult()throws Exception{
  try(var owner=new Browser();var stranger=new Browser()){
   UUID actor=owner.account(false),foreign=stranger.account(false);var f=fixture(actor);readFixtures.add(f.tournament().id);var t=f.tournament();var original=t.matches.getFirst();
   for(int i=0;i<26;i++){var m=new TournamentMatch(UUID.randomUUID().toString(),t.id,original.participantA,original.participantB);m.status=TournamentMatch.Status.FAILED;m.round=2+i;t.matches.add(m);}
   var outsider=new TournamentParticipant(UUID.randomUUID().toString(),null,"secret","Other",1,foreign);t.matches.add(new TournamentMatch(UUID.randomUUID().toString(),t.id,outsider,outsider));
   var reply=owner.call(monitor(f)+"?status=ERROR&page=1&size=20","GET",null,null,false).body();assertThat(reply.path("total").asInt()).isEqualTo(26);assertThat(reply.path("items").size()).isEqualTo(6);assertThat(reply.path("counts").path("STARTING").asInt()).isEqualTo(1);assertThat(owner.call(monitor(f).replace("match-monitor","my-matches"),"GET",null,null,false).body().path("total").asInt()).isEqualTo(27);assertThat(stranger.call(monitor(f).replace("match-monitor","my-matches"),"GET",null,null,false).body().path("total").asInt()).isEqualTo(1);
   for(String query:List.of("?size=51","?page=-1","?status=INVALID"))assertThat(owner.call(monitor(f)+query,"GET",null,null,false).status()).isEqualTo(400);
  }
 }
 @Test void localRunningRoomAndHumanIdentityStayDistinctFromRemoteAndBotMetadata()throws Exception{
  try(var owner=new Browser()){
   UUID actor=owner.account(false);var f=fixture(actor);readFixtures.add(f.tournament().id);var human=new TournamentParticipant(UUID.randomUUID().toString(),null,"","Human",1,actor);var m=new TournamentMatch(UUID.randomUUID().toString(),f.tournament().id,human,human);m.status=TournamentMatch.Status.RUNNING;m.onlineMatchId="real-local-room";f.tournament().matches.add(m);
   var rows=owner.call(monitor(f)+"?status=RUNNING","GET",null,null,false).body().path("items");assertThat(rows.size()).isEqualTo(1);assertThat(rows.get(0).path("executionState").asText()).isEqualTo("RUNNING_LOCAL");assertThat(rows.get(0).path("execution").isNull()).isTrue();assertThat(rows.get(0).path("onlineMatchId").asText()).isEqualTo("real-local-room");assertThat(rows.get(0).path("participantA").path("botName").isNull()).isTrue();
  }
 }
 @Test void retryHistoryKeepsPriorExecutionSeparateWithoutLeakingRawRecoveryMessages()throws Exception{
  try(var owner=new Browser()){
   var f=fixture(owner.account(false));readFixtures.add(f.tournament().id);UUID prior=UUID.randomUUID();
   db.update("insert into match_execution_attempts(id,job_id,match_id,worker_id,heartbeat_epoch,status,active_marker,active_slot,assigned_slot,claimed_at,lease_until) select ?,job_id,match_id,worker_id,heartbeat_epoch,'DECLINED',null,null,assigned_slot,?,lease_until from match_execution_attempts where id=?",prior,java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),f.job().executionId());
   var reply=owner.call(monitor(f)+"/"+f.job().match().matchId()+"/attempts","GET",null,null,false);assertThat(reply.status()).isEqualTo(200);assertThat(reply.body().size()).isEqualTo(2);assertThat(reply.body().get(0).path("executionId").asText()).isEqualTo(prior.toString());assertThat(reply.body().get(1).path("executionId").asText()).isEqualTo(f.job().executionId().toString());assertThat(reply.body().toString()).doesNotContain("credential","spec_json","recovery_reason");
   assertThat(owner.call(monitor(f),"GET",null,null,false).body().path("items").get(0).path("execution").path("attempt").asInt()).isEqualTo(2);
  }
 }
}
