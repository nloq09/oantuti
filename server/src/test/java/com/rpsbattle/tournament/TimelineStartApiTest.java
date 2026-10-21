package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.account.domain.User;
import com.rpsbattle.tournament.coding.WorkspaceSandbox;
import com.rpsbattle.tournament.execution.TournamentExecutionService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Full registration -> frozen version -> official PASS -> check-in -> lifecycle/queue admission. */
class TimelineStartApiTest extends FinalValidationApiTest {
 @Test void runningTournamentRejectsTimelineOverride()throws Exception{var cup=entrants(2,2);var t=tournament(cup.base());when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);request(cup.base()+"/timeline/override","POST",Map.of("revision",t.revision,"action","DELAY_START","value",START.plusSeconds(1000),"reason","Cannot change a running cup"),409);assertThat(t.timeline.tournamentStartAt()).isEqualTo(START.plusSeconds(720));}
 @Autowired TimelineStartService starts;
 @Autowired com.rpsbattle.worker.ExecutionQueue matchQueue;
 @MockitoBean TournamentExecutionService execution;
 record Cup(String base,List<User> users){}
 void as(User user)throws Exception{request("/api/auth/logout","POST",Map.of(),200);account=user;request("/api/auth/login","POST",Map.of("username",user.username(),"password","Tournament Test Password"),200);}
 Cup entrants(int count,int checked)throws Exception{
  String base=exam("DISQUALIFY");var users=new ArrayList<User>();users.add(account);var owner=account;
  for(int i=1;i<count;i++){when(clock.instant()).thenReturn(START.plusSeconds(30));request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();users.add(account);var app=request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","PYTHON"),200);var participant=account;as(owner);request(base+"/participant-applications/"+app.path("registration").path("id").asText()+"/review","POST",Map.of("revision",tournament(base).revision,"decision","APPROVED","reason","Start fixture"),200);as(participant);}
  for(var user:users){as(user);when(clock.instant()).thenReturn(START.plusSeconds(130));ready(base,130);}
  freeze(base);enqueue(base);for(var user:users){as(user);finalDone(base);}
  when(clock.instant()).thenReturn(START.plusSeconds(600));for(int i=0;i<checked;i++){as(users.get(i));request(base+"/check-in","POST",Map.of(),200);}as(owner);return new Cup(base,List.copyOf(users));
 }
 @Test void exactStartClosesRosterSchedulesOnceAndUsesFrozenVersions()throws Exception{
  var cup=entrants(3,2);String base=cup.base();var t=tournament(base);when(clock.instant()).thenReturn(START.plusSeconds(720).minusNanos(1));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.REGISTRATION);request(base+"/start","POST",Map.of(),400);
  when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.startedAt).isEqualTo(START.plusSeconds(720));assertThat(t.participants).hasSize(2);assertThat(t.matches).hasSize(1);assertThat(t.registrations.stream().filter(r->r.status()==TournamentRegistration.Status.NOT_CHECKED_IN)).hasSize(1);assertThat(matchQueue.reservedIds()).contains(t.matches.getFirst().id);verify(sandbox,atLeast(2)).verify(any());long revision=t.revision;var ids=t.matches.stream().map(m->m.id).toList();starts.process(t.id);request(base+"/start","POST",Map.of(),200);assertThat(t.revision).isEqualTo(revision);assertThat(t.matches.stream().map(m->m.id)).containsExactlyElementsOf(ids);assertThat(request(base+"/participant-flow","GET",null,200).path("phase").asText()).isEqualTo("RUNNING");assertThat(request(base+"/me","GET",null,200).path("states").size()).isPositive();
 }
 @Test void insufficientCheckInsCancelOnceWithoutCreatingOrQueuingMatches()throws Exception{
  var cup=entrants(2,1);var t=tournament(cup.base());when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.CANCELLED);assertThat(t.matches).isEmpty();assertThat(t.startedAt).isNull();assertThat(TimelineStartService.issue(t)).isEqualTo("NOT_ENOUGH_CHECKED_IN_BOTS");long revision=t.revision;starts.process(t.id);assertThat(t.revision).isEqualTo(revision);
 }
 @Test void fullQueueRollsBackScheduleAndCanRetryWithoutDuplicateAudit()throws Exception{
  var cup=entrants(2,2);var t=tournament(cup.base());when(clock.instant()).thenReturn(START.plusSeconds(720));int capacity=matchQueue.capacity();org.springframework.test.util.ReflectionTestUtils.setField(matchQueue,"capacity",0);
  try{starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.REGISTRATION);assertThat(t.startedAt).isNull();assertThat(t.matches).isEmpty();assertThat(t.rounds).isEmpty();assertThat(TimelineStartService.issue(t)).isEqualTo("SERVER_BUSY");long revision=t.revision;starts.process(t.id);assertThat(t.revision).isEqualTo(revision);}finally{org.springframework.test.util.ReflectionTestUtils.setField(matchQueue,"capacity",capacity);}
  starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.matches).hasSize(1);assertThat(TimelineStartService.issue(t)).isNull();
 }
 @Test void changedArtifactBlocksStartAndDoesNotExposePrivateFailure()throws Exception{
  var cup=entrants(2,2);var t=tournament(cup.base());when(clock.instant()).thenReturn(START.plusSeconds(720));doThrow(new WorkspaceSandbox.Failure("ARTIFACT_CHANGED")).when(sandbox).verify(any());starts.process(t.id);assertThat(t.matches).isEmpty();assertThat(t.startedAt).isNull();assertThat(TimelineStartService.issue(t)).isEqualTo("ARTIFACT_CHANGED");doNothing().when(sandbox).verify(any());starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);
 }
 @Test void manualStartSharesTheTimelinePipelineWithRequiredCheckInFlag()throws Exception{
  var cup=entrants(2,2);var t=tournament(cup.base());ownedTournaments.submissionMutation(t,com.rpsbattle.auth.AuthService.sessionAuthentication(account),()->{t.checkInRequired=true;tournaments.changed(t);});when(clock.instant()).thenReturn(START.plusSeconds(720));request(cup.base()+"/start","POST",Map.of(),200);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.matches).hasSize(1);
 }
 @Test void disabledAccountIsExcludedEvenAfterSuccessfulCheckIn()throws Exception{
  var cup=entrants(2,2);var t=tournament(cup.base());var absent=cup.users().get(1);ownedTournaments.timelineUsers.save(absent.withStatus(com.rpsbattle.account.domain.AccountStatus.DISABLED,START.plusSeconds(700)));when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.CANCELLED);assertThat(t.participants).hasSize(1);assertThat(t.registrations.stream().filter(r->r.userId().equals(absent.id().value())).findFirst().orElseThrow().status()).isEqualTo(TournamentRegistration.Status.REJECTED);assertThat(t.matches).isEmpty();
 }

 @Test void infrastructureValidationHoldsNoShowAndStartUntilAuditedExtensionAndRetry()throws Exception{
  var cup=entrants(2,1);var t=tournament(cup.base());var waiting=cup.users().get(1);var owner=account;
  var validation=jdbc.queryForObject("select id from tournament_final_validations where tournament_id=? and user_id=?",UUID.class,UUID.fromString(t.id),waiting.id().value());
  jdbc.update("update tournament_final_validations set status='INFRA_ERROR',reason='WORKER_RECOVERY_EXHAUSTED' where id=?",validation);
  when(clock.instant()).thenReturn(START.plusSeconds(660));ownedTournaments.expireTimelineCheckIns();assertThat(t.registrations.stream().filter(r->r.userId().equals(waiting.id().value())).findFirst().orElseThrow().status()).isEqualTo(TournamentRegistration.Status.APPROVED);
  when(clock.instant()).thenReturn(START.plusSeconds(720));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.REGISTRATION);assertThat(t.startedAt).isNull();assertThat(TimelineStartService.issue(t)).isEqualTo("VALIDATION_INFRA_BLOCKED");long revision=t.revision;starts.process(t.id);assertThat(t.revision).isEqualTo(revision);
  request(cup.base()+"/timeline/override","POST",Map.of("revision",t.revision,"action","EXTEND_VALIDATION","value",START.plusSeconds(900),"reason","Worker outage: reopen validation and shift check-in"),200);
  assertThat(t.timeline.checkInOpenAt()).isEqualTo(START.plusSeconds(900));request(cup.base()+"/final-validations/"+validation+"/retry","POST",Map.of("revision",t.revision,"reason","Worker recovered"),200);
  as(waiting);assertThat(finalDone(cup.base()).path("submissionStatus").asText()).isEqualTo("BOT_READY");assertThat(TimelineStartService.issue(t)).isNull();when(clock.instant()).thenReturn(START.plusSeconds(900));request(cup.base()+"/check-in","POST",Map.of(),200);as(owner);when(clock.instant()).thenReturn(START.plusSeconds(1020));starts.process(t.id);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.participants).hasSize(2);
 }
}
