package com.rpsbattle.tournament;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.codevalidation.BotTestService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"rps.coding.worker-auto-dispatch=false"})
class SubmissionAttemptApiTest extends StageThreeWebFixture {
 @MockitoBean WorkspaceSandbox sandbox;
 @MockitoBean BotTestService tests;
 @Autowired CodingWorker worker;
 @Autowired CodingQueueStore queue;
 @Autowired TournamentSubmissionService submissions;
 @Autowired SubmissionAttemptStore attempts;
 @Autowired SubmissionStore submissionStore;
 @Autowired OwnedTournamentService organizer;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator persistence;
 @BeforeEach void mocks(){when(sandbox.build(any(),any())).thenReturn(new WorkspaceSandbox.Build(true,UUID.randomUUID(),"a".repeat(64),"Build successful"));when(tests.test(any())).thenReturn(pass());when(tests.hidden(any())).thenReturn(new BotTestService.HiddenResult(true,List.of(new BotTestService.HiddenCase("Hidden Test 1","PASS"))));}
 BotTestService.Result pass(){return new BotTestService.Result(true,"PUBLIC",List.of(),8,"PUBLIC_TESTS_PASSED",0,0,1,"",Map.of());}
 String cup(Map<String,Object> policy)throws Exception{loginAccount();var t=participantTournament(Map.of("name","Single submit","botPolicy",policy));String base="/api/tournaments/"+t.path("id").asText();managerRequest(base+"/open-registration","POST",Map.of(),200);return base;}
 Map<String,Object> body(UUID id,String source){return Map.of("requestId",id,"language","PYTHON","sourceCode",source,"rulesAccepted",true);}
 JsonNode start(String base,String source)throws Exception{return request(base+"/submission/me/attempts","POST",body(UUID.randomUUID(),source),202);}
 JsonNode done(String base)throws Exception{worker.dispatchJobs();long end=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(System.nanoTime()<end){var a=request(base+"/submission/me/attempts/latest","GET",null,200);if(Set.of("SUCCEEDED","FAILED").contains(a.path("status").asText()))return a;Thread.sleep(20);}throw new AssertionError("Attempt did not finish");}
 JsonNode mine(String base)throws Exception{return request(base+"/submission/me","GET",null,200);}
 void expire(String base)throws Exception{var t=tournaments.find(base.substring(base.lastIndexOf('/')+1));var policy=(ObjectNode)json.valueToTree(t.botPolicy);policy.put("submissionDeadline",Instant.now().minusSeconds(1).toString());t.botPolicy=json.treeToValue(policy,com.rpsbattle.tournament.domain.TournamentBotPolicy.class);tournaments.changed(t);}

 @Test void oneRequestSnapshotsSourceAndOnlyPromotesAfterAllRequiredTestsWithoutOptionalSample()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH","hiddenTestsEnabled",true,"allowCustomTestMatch",false,"allowPublicTests",false));String source="print('captured source')";
  var started=start(base,source);assertThat(started.path("status").asText()).isEqualTo("QUEUED");assertThat(mine(base).path("status").asText()).isEqualTo("NO_SUBMISSION");
  var draft=request(base+"/workspace/me?language=PYTHON","GET",null,200);request(base+"/workspace/me","PUT",Map.of("language","PYTHON","revision",draft.path("revision").asLong(),"sourceCode","print('next draft')"),200);
  var finished=done(base);assertThat(finished.path("status").asText()).isEqualTo("SUCCEEDED");assertThat(mine(base).path("versionId")).isEqualTo(started.path("versionId"));
  var snapshot=request(base+"/workspace/versions/"+started.path("versionId").asText()+"/source","GET",null,200);assertThat(snapshot.path("sourceCode").asText()).isEqualTo(source);
  assertThat(request(base+"/workspace/me?language=PYTHON","GET",null,200).path("sourceCode").asText()).isEqualTo("print('next draft')");verify(tests,never()).sample(any());verify(tests).hidden(any());
 }
 @Test void failedBuildAndFailedTestPreserveSuccessfulSubmissionAndHistory()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));start(base,"print('v1')");assertThat(done(base).path("status").asText()).isEqualTo("SUCCEEDED");var original=mine(base);
  when(sandbox.build(eq("PYTHON"),eq("broken source"))).thenReturn(new WorkspaceSandbox.Build(false,null,null,"SyntaxError: line 3"));start(base,"broken source");var build=done(base);assertThat(build.path("status").asText()).isEqualTo("FAILED");assertThat(build.path("buildLog").asText()).contains("line 3");assertThat(mine(base).path("versionId")).isEqualTo(original.path("versionId"));
  when(tests.test(any())).thenReturn(new BotTestService.Result(false,"PUBLIC",List.of(new BotTestService.Case("1","PASS","",null,"",1),new BotTestService.Case("2","PASS","",null,"",1),new BotTestService.Case("3","FAIL","PROTOCOL_ERROR",null,"",1)),3,"FAIL",0,1,1,"",Map.of()));start(base,"print('bad protocol')");var test=done(base);assertThat(test.path("errorDetail").asText()).contains("3","PROTOCOL_ERROR");assertThat(mine(base).path("versionId")).isEqualTo(original.path("versionId"));assertThat(request(base+"/submission/me/versions","GET",null,200)).hasSize(3);
 }
 @Test void duplicateRequestIsIdempotentAndReloadRestoresProgressWhileAnotherAttemptIsBlocked()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));var id=UUID.randomUUID();var body=body(id,"print('once')");var first=request(base+"/submission/me/attempts","POST",body,202);
  assertThat(request(base+"/submission/me/attempts","POST",body,202).path("versionId")).isEqualTo(first.path("versionId"));assertThat(request(base+"/submission/me/attempts/latest","GET",null,200).path("id").asText()).isEqualTo(id.toString());
  request(base+"/submission/me/attempts","POST",body(id,"print('changed')"),409);request(base+"/submission/me/attempts","POST",body(UUID.randomUUID(),"print('second')"),409);done(base);expire(base);
  assertThat(request(base+"/submission/me/attempts","POST",body,202).path("status").asText()).isEqualTo("SUCCEEDED");verify(sandbox,times(1)).build(any(),any());assertThat(request(base+"/submission/me/versions","GET",null,200)).hasSize(1);
 }
 @Test void completionAfterDeadlineFailsAndDoesNotReplaceThePreviouslySubmittedBot()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));start(base,"print('previous')");done(base);var original=mine(base);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  when(sandbox.build(eq("PYTHON"),eq("print('late')"))).thenAnswer(call->{entered.countDown();release.await(5,TimeUnit.SECONDS);return new WorkspaceSandbox.Build(true,UUID.randomUUID(),"b".repeat(64),"Built after cutoff");});start(base,"print('late')");worker.dispatchJobs();assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();try{expire(base);}finally{release.countDown();}var late=done(base);assertThat(late.path("status").asText()).isEqualTo("FAILED");assertThat(late.path("errorCode").asText()).isEqualTo("WORKSPACE_CLOSED");assertThat(mine(base).path("versionId")).isEqualTo(original.path("versionId"));request(base+"/submission/me/attempts","POST",body(UUID.randomUUID(),"print('too late')"),409);
 }
 @Test void limitsRulesLocksAndOwnershipAreCheckedBeforeEnqueue()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH","maxSubmissionVersions",1,"maxSourceBytes",30,"lockOnSubmit",true));request(base+"/submission/me/attempts","POST",Map.of("requestId",UUID.randomUUID(),"language","PYTHON","sourceCode","print(1)","rulesAccepted",false),400);request(base+"/submission/me/attempts","POST",body(UUID.randomUUID(),"a".repeat(31)),400);
  start(base,"print(1)");done(base);request(base+"/submission/me/attempts","POST",body(UUID.randomUUID(),"print(2)"),409);String version=mine(base).path("versionId").asText();request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();assertThat(request(base+"/submission/me/attempts/latest","GET",null,200).path("status").asText()).isEqualTo("NO_SUBMISSION");request(base+"/workspace/versions/"+version+"/source","GET",null,404);
 }
 @Test void noDraftSavePolicyAndImportedBotBothUseTheSamePipeline()throws Exception{
  String base=cup(Map.of("submissionMode","CODE_IN_PLATFORM_ONLY","allowDraftSave",false));start(base,"print('without draft save')");assertThat(done(base).path("status").asText()).isEqualTo("SUCCEEDED");
  String imported=cup(Map.of("submissionMode","PRE_UPLOADED_ONLY"));var bot=upload("Import for single submit");request(imported+"/submission/me/attempts","POST",Map.of("requestId",UUID.randomUUID(),"botId",bot.id(),"rulesAccepted",true),202);assertThat(done(imported).path("status").asText()).isEqualTo("SUCCEEDED");assertThat(mine(imported).path("sourceType").asText()).isEqualTo("PRE_UPLOADED");request(imported+"/submission/me/attempts","POST",body(UUID.randomUUID(),"print('bypass')"),403);
 }
 @Test void hiddenFailureNeverPromotes()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH","hiddenTestsEnabled",true));when(tests.hidden(any())).thenReturn(new BotTestService.HiddenResult(false,List.of(new BotTestService.HiddenCase("Hidden Test 1","FAIL"))));start(base,"print('fails hidden')");assertThat(done(base).path("errorCode").asText()).isEqualTo("HIDDEN_TEST_FAILED");assertThat(mine(base).path("status").asText()).isEqualTo("NO_SUBMISSION");
 }
 @Test void expiredLeaseRetriesTheSameVersionAndCannotAcceptCompletionFromTheOldWorker()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));var started=start(base,"print('recover')");var first=queue.lease();assertThat(first.pipelineSubmission()).isTrue();
  jdbc.update("update bot_coding_jobs set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),first.id());var second=queue.lease();assertThat(second.work().versionId()).isEqualTo(first.work().versionId());assertThat(second.token()).isNotEqualTo(first.token());
  assertThat(attempts.built(first,new WorkspaceSandbox.Build(true,UUID.randomUUID(),"f".repeat(64),"Stale completion"))).isNull();assertThat(mine(base).path("status").asText()).isEqualTo("NO_SUBMISSION");
  for(int n=0;n<2;n++){jdbc.update("update bot_coding_jobs set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),first.id());second=queue.lease();}
  assertThat(request(base+"/submission/me/attempts/latest","GET",null,200).path("errorCode").asText()).isEqualTo("SUBMISSION_RETRY_EXHAUSTED");assertThat(request(base+"/submission/me/versions","GET",null,200)).hasSize(1);assertThat(started.path("version").asInt()).isEqualTo(1);
 }
 @Test void finalPersistenceFailureRollsBackBothTheSubmissionAndTournamentRegistration()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));start(base,"print('stable')");done(base);var original=mine(base);var t=tournaments.find(base.substring(base.lastIndexOf('/')+1));var registrations=List.copyOf(t.registrations);long revision=t.revision;
  start(base,"print('transaction must rollback')");doThrow(new IllegalStateException("Injected persistence failure")).when(persistence).persistNow(t);
  try{assertThat(done(base).path("status").asText()).isEqualTo("FAILED");assertThat(mine(base).path("versionId")).isEqualTo(original.path("versionId"));assertThat(t.registrations).containsExactlyElementsOf(registrations);assertThat(t.revision).isEqualTo(revision);}finally{doCallRealMethod().when(persistence).persistNow(t);}
 }
 @Test void organizerRetestingAPipelineVersionIsNotTreatedAsANewSubmissionAttempt()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));start(base,"print('retest')");done(base);var s=mine(base);managerRequest(base+"/submissions/"+s.path("id").asText()+"/action","POST",Map.of("action","retest","reason","Review protocol","revision",request(base,"GET",null,200).path("revision").asLong()),200);var lease=queue.lease();assertThat(lease.pipelineSubmission()).isFalse();assertThat(lease.kind()).isEqualTo("VALIDATE");queue.complete(lease,null,"{\"passed\":true}",true,"{\"passed\":true}",true,null,null);assertThat(request(base+"/submission/me/attempts/latest","GET",null,200).path("status").asText()).isEqualTo("SUCCEEDED");
 }
 @Test void reopeningTheCodingWindowInvalidatesPendingAttemptsAndAllowsANewRequest()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));start(base,"print('old window')");var t=tournaments.find(base.substring(base.lastIndexOf('/')+1));organizer.submissionMutation(t,com.rpsbattle.auth.AuthService.sessionAuthentication(account),()->{submissionStore.reopenTimeline(t,account.id().value());tournaments.changed(t);});assertThat(request(base+"/submission/me/attempts/latest","GET",null,200).path("errorCode").asText()).isEqualTo("CODING_WINDOW_CHANGED");start(base,"print('new window')");assertThat(done(base).path("status").asText()).isEqualTo("SUCCEEDED");
 }

}
