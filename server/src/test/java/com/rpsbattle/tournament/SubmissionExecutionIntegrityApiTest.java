package com.rpsbattle.tournament;

import com.rpsbattle.auth.AuthService;
import com.rpsbattle.codevalidation.BotTestService;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.worker.remote.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real submission, freeze, validation, roster and remote queue; only sandbox execution is stubbed. */
class SubmissionExecutionIntegrityApiTest extends TimelineStartApiTest {
 @Autowired RemoteMatchQueue remoteQueue;
 @Autowired WorkerIdentityService identities;
 @Autowired WorkerTelemetryService telemetry;

 private UUID submitAttempt(String base,String language,String source,int offset)throws Exception {
  when(clock.instant()).thenReturn(START.plusSeconds(offset));
  request(base+"/submission/me/attempts","POST",Map.of("requestId",UUID.randomUUID(),"language",language,"sourceCode",source,"rulesAccepted",true),202);
  long until=System.nanoTime()+Duration.ofSeconds(10).toNanos();
  while(System.nanoTime()<until){
   worker.dispatchJobs();var attempt=request(base+"/submission/me/attempts/latest","GET",null,200);
   assertThat(attempt.path("status").asText()).as(attempt.toString()).isNotEqualTo("FAILED");
   if(attempt.path("status").asText().equals("SUCCEEDED")){
    UUID version=UUID.fromString(attempt.path("versionId").asText());
    jdbc.update("update tournament_submission_versions set created_at=?,updated_at=? where id=?",java.sql.Timestamp.from(START.plusSeconds(offset)),java.sql.Timestamp.from(START.plusSeconds(offset+1)),version);
    jdbc.update("update tournament_bot_submissions set submitted_at=? where tournament_id=? and user_id=?",java.sql.Timestamp.from(START.plusSeconds(offset+1)),UUID.fromString(id(base)),account.id().value());return version;
   }
   Thread.sleep(25);
  }
  throw new AssertionError("Submission attempt timed out");
 }
 private String source(String language,int version){return language.equals("JAVA")?"class Main { public static void main(String[] args) { System.out.println("+version+"); } }":language.equals("CPP")?"int main() { return "+version+"; }":"print("+version+")";}
 record Prepared(String base,UUID selected,UUID replaced){}
 private Prepared prepared(String language,boolean resubmit,boolean fallback)throws Exception {
  doAnswer(call->{String l=call.getArgument(0),source=call.getArgument(1);String bytes=l.equals("PYTHON")?source:"compiled:"+source;return new WorkspaceSandbox.Build(true,UUID.randomUUID(),WorkspaceSandbox.hash(bytes.getBytes(StandardCharsets.UTF_8)),"Build successful");}).when(sandbox).build(any(),any());
  String base=exam(fallback?"USE_LAST_VALID_VERSION":"DISQUALIFY");var first=account;
  when(clock.instant()).thenReturn(START.plusSeconds(30));request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();var second=account;
  var application=request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage",language),200);
  managerRequest(base+"/participant-applications/"+application.path("registration").path("id").asText()+"/review","POST",Map.of("revision",tournament(base).revision,"decision","APPROVED","reason","Execution integrity fixture"),200);
  UUID selected=null,replaced=null;
  for(var person:List.of(first,second)){
   as(person);UUID initial=submitAttempt(base,language,source(language,1),130);
   if(person==first){selected=initial;if(resubmit){replaced=initial;selected=submitAttempt(base,language,source(language,2),155);}}
  }
  if(fallback){UUID failedVersion=selected;doAnswer(call->new BotTestService.HiddenResult(!((WorkspaceStore.Work)call.getArgument(0)).versionId().equals(failedVersion),List.of())).when(tests).hidden(any());UUID tmp=selected;selected=replaced;replaced=tmp;}
  freeze(base);enqueue(base);for(var person:List.of(first,second)){as(person);assertThat(finalDone(base).path("submissionStatus").asText()).isEqualTo("BOT_READY");}
  when(clock.instant()).thenReturn(START.plusSeconds(600));for(var person:List.of(first,second)){as(person);request(base+"/check-in","POST",Map.of(),200);}
  as(first);when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(id(base));assertThat(tournament(base).status).isEqualTo(TournamentStatus.RUNNING);
  assertThat(submissions.mine(id(base),first.id().value()).versionId()).isEqualTo(selected);return new Prepared(base,selected,replaced);
 }
 private void assertQueuedVersion(Prepared prepared,String language)throws Exception {
  var t=tournament(prepared.base());var match=t.matches.getFirst();UUID actor=organizer(prepared.base()).user().id().value();
  var enrolled=identities.register(new WorkerIdentityService.Registration(identities.issue(actor,1,15).registrationToken(),"Integrity worker","0.10.0",1));UUID machine=enrolled.worker().workerId();
  long epoch=telemetry.capabilities(machine,new WorkerTelemetryService.Capabilities(4,8000L,"Linux",true,Set.of(language),1,2,"0.10.0",1)).epoch();
  telemetry.heartbeat(machine,new WorkerTelemetryService.Heartbeat(epoch,1L,0,1,0,0.0,0.0,8000L,0L),"local");telemetry.review(machine,actor,new WorkerTelemetryService.Review(0,"APPROVE","TRUSTED","Regression test worker"));
  try{
   assertThat(routing.route(t,match)).isTrue();assertThat(routing.route(t,match)).isTrue();
   assertThat(jdbc.queryForObject("select count(*) from match_execution_jobs where match_id=?",Integer.class,UUID.fromString(match.id))).isEqualTo(1);
   var spec=json.readValue(jdbc.queryForObject("select spec_json from match_execution_jobs where match_id=?",String.class,UUID.fromString(match.id)),RemoteMatchQueue.Spec.class);
   for(var seat:List.of(match.participantA,match.participantB)){
    var expected=submissions.execution(t.id,seat.participantId(),seat.botId());var actual=seat==match.participantA?spec.botA():spec.botB();
    assertThat(actual.artifactId()).isEqualTo(expected.artifactId());assertThat(actual.artifactHash()).isEqualTo(expected.artifactHash());assertThat(actual.language()).isEqualTo(language);
    if(seat.ownerUserId().equals(account.id().value()))assertThat(expected.versionId()).isEqualTo(prepared.selected());
   }
   var assigned=remoteQueue.claim(machine,new RemoteMatchQueue.Claim(UUID.randomUUID(),epoch,1,List.of(),"1","classic-online-v1",1));
   assertThat(assigned.jobs()).hasSize(1);assertThat(assigned.jobs().getFirst().match()).isEqualTo(spec);
  }finally{jdbc.update("update workers set status='DISABLED' where id=?",machine);}
 }
 @ParameterizedTest @ValueSource(strings={"CPP","JAVA"})
 void regressionCompiledVersionRoutesUsingArtifactHash(String language)throws Exception {var prepared=prepared(language,false,false);assertQueuedVersion(prepared,language);}
 @Test void regressionResubmissionSupportsAutoLocalFallbackAndRemoteRouting()throws Exception {
  var prepared=prepared("PYTHON",true,false);var t=tournament(prepared.base());var seat=t.participants.stream().filter(p->p.ownerUserId().equals(account.id().value())).findFirst().orElseThrow();
  assertThat(seat.artifactHash()).isNotEqualTo(submissions.execution(t.id,seat.participantId(),seat.botId()).artifactHash());
  assertThat(routing.route(t,t.matches.getFirst())).isFalse();assertQueuedVersion(prepared,"PYTHON");
 }
 @Test void regressionFallbackQueuesTheRevalidatedVersion()throws Exception {assertQueuedVersion(prepared("PYTHON",true,true),"PYTHON");}
 @Test void regressionCorruptLockedVersionStillCannotRoute()throws Exception {
  var prepared=prepared("CPP",false,false);var t=tournament(prepared.base());
  jdbc.update("update tournament_bot_submissions set artifact_hash=? where tournament_id=? and user_id=?","f".repeat(64),UUID.fromString(t.id),account.id().value());
  assertThatThrownBy(()->routing.route(t,t.matches.getFirst())).hasMessage("BOT_ARTIFACT_CHANGED");
  assertThat(jdbc.queryForObject("select count(*) from match_execution_jobs where tournament_id=?",Integer.class,UUID.fromString(t.id))).isZero();
 }
 private UUID rejectMySubmission(String base)throws Exception {
  var entry=request(base+"/submission/me","GET",null,200);UUID registrationId=UUID.fromString(entry.path("registrationId").asText());
  managerRequest(base+"/submissions/"+entry.path("id").asText()+"/action","POST",Map.of("revision",tournament(base).revision,"action","reject","reason","Disqualify checked in submission"),200);
  assertThat(submissions.mine(id(base),account.id().value()).status()).isEqualTo("DISQUALIFIED");return registrationId;
 }
 @Test void regressionRejectionClearsRosterAndStartsWithRemainingEntrants()throws Exception {
  var cup=entrants(3,3);var t=tournament(cup.base());UUID registrationId=rejectMySubmission(cup.base());
  assertThat(t.participants).hasSize(2);assertThat(jdbc.queryForObject("select status from tournament_registrations where id=?",String.class,registrationId)).isEqualTo("REJECTED");
  assertThat(jdbc.queryForObject("select count(*) from tournament_participants where id=?",Integer.class,registrationId)).isZero();
  var flow=request(cup.base()+"/participant-flow","GET",null,200);assertThat(flow.path("submissionStatus").asText()).isEqualTo("DISQUALIFIED");assertThat(flow.path("checkIn").path("status").asText()).isEqualTo("NOT_ELIGIBLE");assertThat(flow.path("checkIn").path("checkedInCount").asInt()).isEqualTo(2);
  request(cup.base()+"/check-in","POST",Map.of(),400);
  when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.matches).hasSize(1);
  assertThat(t.participants).noneMatch(p->p.participantId().equals(registrationId.toString()));
 }
 @Test void regressionRejectionCancelsOnlyWhenTooFewEntrantsRemain()throws Exception {
  var cup=entrants(2,2);var t=tournament(cup.base());rejectMySubmission(cup.base());when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);
  assertThat(t.status).isEqualTo(TournamentStatus.CANCELLED);assertThat(t.matches).isEmpty();assertThat(TimelineStartService.issue(t)).isEqualTo("NOT_ENOUGH_CHECKED_IN_BOTS");
 }
 @Test void regressionStartRepairsLegacyRejectedCheckedInSeat()throws Exception {
  var cup=entrants(3,3);var t=tournament(cup.base());var s=submissions.mine(t.id,account.id().value());
  // Reproduce a persisted pre-fix record: decision saved, checked-in roster unchanged.
  ownedTournaments.submissionMutation(t,AuthService.sessionAuthentication(organizer(cup.base()).user()),()->{submissions.action(t.id,s.id(),"reject","Legacy decision",organizer(cup.base()).user().id().value());tournaments.changed(t);});
  assertThat(t.participants).hasSize(3);when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);
  assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.participants).hasSize(2);assertThat(t.matches).hasSize(1);
  assertThat(jdbc.queryForObject("select status from tournament_registrations where id=?",String.class,s.registrationId())).isEqualTo("REJECTED");
 }
 @Test void regressionRejectedRosterRollsBackWithSubmissionTransaction()throws Exception {
  var cup=entrants(2,2);var t=tournament(cup.base());var s=submissions.mine(t.id,account.id().value());long revision=t.revision;
  var auth=AuthService.sessionAuthentication(organizer(cup.base()).user());
  assertThatThrownBy(()->ownedTournaments.submissionMutation(t,auth,()->{
   submissionService.action(auth,t.id,s.id(),"reject","Rollback decision",t.revision);throw new IllegalStateException("Rollback probe");
  })).hasMessage("Rollback probe");
  assertThat(submissions.mine(t.id,account.id().value()).status()).isEqualTo("LOCKED");assertThat(t.revision).isEqualTo(revision);assertThat(t.participants).hasSize(2);
  assertThat(jdbc.queryForObject("select status from tournament_registrations where id=?",String.class,s.registrationId())).isEqualTo("CHECKED_IN");
 }
 @Autowired TournamentSubmissionService submissionService;
}
