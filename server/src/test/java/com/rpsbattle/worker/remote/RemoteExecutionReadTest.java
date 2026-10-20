package com.rpsbattle.worker.remote;
import com.rpsbattle.tournament.RemoteExecutionReadService;
import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class RemoteExecutionReadTest extends RemoteResultTest {
 final List<String> readFixtures=new ArrayList<>();
 @org.junit.jupiter.api.AfterEach void removeReadOnlyRegistryFixtures(){readFixtures.forEach(registry::remove);}
 @Autowired RemoteExecutionReadService read;
 String path(UUID tournament){return "/api/tournaments/"+tournament+"/remote-executions";}
 void register(RemoteMatchQueue.Spec spec,UUID actor){var t=new Tournament(spec.tournamentId().toString(),"Private read fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=actor;registry.register(t);readFixtures.add(t.id);}
 @Test void claimStartupGameplayReceiptAndProjectionHaveDistinctAuthoritativeStates()throws Exception{
  try(var admin=new Browser();var machineHttp=new Browser()){
   UUID actor=admin.account(true);var f=fixture(actor);String path=path(f.job().match().tournamentId());
   var starting=admin.call(path,"GET",null,null,false);assertThat(starting.status()).isEqualTo(200);assertThat(starting.body().path("items").get(0).path("executionState").asText()).isEqualTo("STARTING");
   var body=new RemoteMatchQueue.Start(f.job().executionId(),f.machine().epoch());String running="/internal/workers/"+f.machine().id()+"/jobs/"+f.job().jobId()+"/running";
   assertThat(admin.call(running,"POST",body,null,true).status()).isEqualTo(401);assertThat(machineHttp.call(running,"POST",body,f.machine().enrolled().workerCredential(),false).status()).isEqualTo(200);
   Instant began=db.queryForObject("select gameplay_started_at from match_execution_attempts where id=?",java.sql.Timestamp.class,f.job().executionId()).toInstant();queue.gameplay(f.machine().id(),f.job().jobId(),body);assertThat(db.queryForObject("select gameplay_started_at from match_execution_attempts where id=?",java.sql.Timestamp.class,f.job().executionId()).toInstant()).isEqualTo(began);
   assertThat(admin.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("RUNNING_REMOTE");
   results.accept(f.machine().id(),f.job().jobId(),f.upload());assertThat(admin.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("RESULT_PENDING");projector.project(f.job().executionId());assertThat(admin.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("FINISHED");
  }
 }
 @Test void queuedAssignedExpiredAndReassignedReadStatesKeepAttemptIdentity()throws Exception{
  try(var owner=new Browser()){
   UUID actor=owner.account(false);var spec=spec(actor,"PYTHON","PYTHON","TRUSTED");register(spec,actor);queue.enqueue(spec);var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");String path=path(spec.tournamentId());
   var first=owner.call(path,"GET",null,null,false).body().path("items").get(0);assertThat(first.path("executionState").asText()).isEqualTo("QUEUED");assertThat(first.path("queuePosition").asInt()).isEqualTo(1);assertThat(first.path("attempt").asInt()).isZero();var job=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();assertThat(owner.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("ASSIGNED");
   db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),job.executionId());assertThat(owner.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("RECOVERING");
   db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().plusSeconds(60)),job.executionId());queue.decline(machine.id(),job.jobId(),new RemoteMatchQueue.Decline(job.executionId(),"ARTIFACT_UNAVAILABLE"));assertThat(owner.call(path,"GET",null,null,false).body().path("items").get(0).path("executionState").asText()).isEqualTo("RETRYING");var next=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();var second=owner.call(path,"GET",null,null,false).body().path("items").get(0);assertThat(second.path("executionId").asText()).isEqualTo(next.executionId().toString());assertThat(second.path("attempt").asInt()).isEqualTo(2);
  }
 }
 @Test void ownerScopePrivacyFiltersAndPaginationNeverExposeGlobalQueueOrSecrets()throws Exception{
  try(var owner=new Browser();var stranger=new Browser();var guest=new Browser()){
   UUID actor=owner.account(false);var spec=spec(actor,"PYTHON","PYTHON","TRUSTED");register(spec,actor);queue.enqueue(spec);String path=path(spec.tournamentId());
   for(int i=0;i<27;i++){UUID match=UUID.randomUUID();db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) select ?,tournament_id,participant_a_id,participant_b_id,'PENDING' from tournament_matches where id=?",match,spec.matchId());queue.enqueue(new RemoteMatchQueue.Spec(match,spec.tournamentId(),spec.botA(),spec.botB(),"1","classic-online-v1",7,1000,60000,200,"TRUSTED"));}
   var foreign=spec(actor,"PYTHON","PYTHON","TRUSTED");queue.enqueue(foreign);var reply=owner.call(path+"?status=QUEUED&page=1&size=25","GET",null,null,false);assertThat(reply.status()).isEqualTo(200);assertThat(reply.body().path("total").asInt()).isEqualTo(28);assertThat(reply.body().path("items").size()).isEqualTo(3);assertThat(reply.body().path("counts").path("QUEUED").asInt()).isEqualTo(28);assertThat(reply.headers().firstValue("Cache-Control").orElse("")).contains("no-store");assertThat(reply.body().toString()).doesNotContain("spec_json","botToken","credential","private-fixture","sourceCode","replay_json",foreign.tournamentId().toString());
   stranger.account(false);assertThat(stranger.call(path,"GET",null,null,false).status()).isEqualTo(403);assertThat(guest.call(path,"GET",null,null,false).status()).isEqualTo(401);assertThat(owner.call(path+"?status=INVALID","GET",null,null,false).status()).isEqualTo(400);assertThat(owner.call(path+"?size=101","GET",null,null,false).status()).isEqualTo(400);
  }
 }
 @Test void runningSignalCannotBeSentByAnotherWorkerOrExpiredSession()throws Exception{
  try(var admin=new Browser();var http=new Browser()){
   UUID actor=admin.account(true);var f=fixture(actor);var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");String path="/internal/workers/"+f.machine().id()+"/jobs/"+f.job().jobId()+"/running";var body=new RemoteMatchQueue.Start(f.job().executionId(),f.machine().epoch());assertThat(http.call(path,"POST",body,other.enrolled().workerCredential(),false).status()).isEqualTo(403);assertThat(http.call(path,"POST",new RemoteMatchQueue.Start(body.executionId(),body.epoch()+1),f.machine().enrolled().workerCredential(),false).status()).isEqualTo(409);db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),body.executionId());assertThat(http.call(path,"POST",body,f.machine().enrolled().workerCredential(),false).status()).isEqualTo(409);assertThat(db.queryForObject("select gameplay_started_at from match_execution_attempts where id=?",java.sql.Timestamp.class,body.executionId())).isNull();
  }
 }
 @Test void terminalAndUnknownFactsCannotPretendAResultWasApplied(){
  assertThat(RemoteExecutionReadService.state("RUNNING","PENDING","DONE","DONE",true,"PENDING",false,true,false,1)).isEqualTo("RESULT_PENDING");assertThat(RemoteExecutionReadService.state("CANCELLED","PENDING","DONE","DONE",true,"PENDING",false,true,false,1)).isEqualTo("CANCELLED");assertThat(RemoteExecutionReadService.state("RUNNING","FAILED","CANCELLED","EXPIRED",false,null,true,false,true,3)).isEqualTo("ERROR");assertThat(RemoteExecutionReadService.state("RUNNING","PENDING","DONE","DONE",false,null,false,true,false,1)).isEqualTo("UNKNOWN");
 }
}
