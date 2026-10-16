package com.rpsbattle.tournament;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.codevalidation.BotTestService;
import com.rpsbattle.auth.AuthService;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentSubmissionApiTest extends StageThreeWebFixture {
 @MockitoBean WorkspaceSandbox sandbox;
 @MockitoBean BotTestService tests;
 @Autowired TournamentSubmissionService submissions;
 @BeforeEach void results(){when(sandbox.build(any(),any())).thenReturn(new WorkspaceSandbox.Build(true,UUID.randomUUID(),"a".repeat(64),"Build successful"));var pass=new BotTestService.Result(true,"PUBLIC",List.of(),8,"PASS",0,0,1,"",Map.of());when(tests.test(any())).thenReturn(pass);when(tests.sample(any())).thenReturn(pass);when(tests.hidden(any())).thenReturn(new BotTestService.HiddenResult(true,List.of(new BotTestService.HiddenCase("Hidden Test 1","PASS"))));}
 String cup(Map<String,Object> policy)throws Exception{loginAccount();var t=request("/api/tournaments","POST",Map.of("name","Submission workflow","botPolicy",policy),201);String base="/api/tournaments/"+t.path("id").asText();request(base+"/open-registration","POST",Map.of(),200);return base;}
 com.fasterxml.jackson.databind.JsonNode done(String base)throws Exception{long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();while(System.nanoTime()<deadline){var w=request(base+"/workspace/me?language=PYTHON","GET",null,200);if(!Set.of("BUILDING","TESTING").contains(w.path("status").asText()))return w;Thread.sleep(25);}throw new AssertionError("Durable job not finished");}
 com.fasterxml.jackson.databind.JsonNode ready(String base)throws Exception{var w=request(base+"/workspace","POST",Map.of("language","PYTHON"),200);request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('private version')\n"),202);w=done(base);request(base+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);w=done(base);assertThat(w.path("status").asText()).isEqualTo("READY");return w;}
 com.fasterxml.jackson.databind.JsonNode submit(String base,com.fasterxml.jackson.databind.JsonNode w)throws Exception{return request(base+"/workspace/me/submit","POST",Map.of("versionId",w.path("version").path("id").asText(),"revision",request(base,"GET",null,200).path("revision").asLong(),"rulesAccepted",true),200);}
 @Test void hiddenReadySubmissionLocksSourceAndPublishesNoPrivatePayload()throws Exception{
  String base=cup(Map.of("submissionMode","CODE_IN_PLATFORM_ONLY","hiddenTestsEnabled",true,"lockOnSubmit",true));var w=ready(base);assertThat(w.path("version").path("hiddenResult").asText()).contains("Hidden Test 1").doesNotContain("YOUR_TURN","state","stderr");var submitted=submit(base,w);assertThat(submitted.path("status").asText()).isEqualTo("LOCKED");var own=request(base+"/workspace/me?language=PYTHON","GET",null,200);request(base+"/workspace/me","PUT",Map.of("language","PYTHON","revision",own.path("revision").asLong(),"sourceCode","print('replace')"),409);request(base+"/workspace","POST",Map.of("language","CPP"),409);
  String publicView=request(base,"GET",null,200).toString();assertThat(publicView).doesNotContain("private version","sourceCode","hiddenResult");assertThat(request(base+"/submissions","GET",null,200).toString()).doesNotContain("private version","sourceCode","YOUR_TURN");
 }
 @Test void ownershipCannotBeSpoofedAndOnlyOwnerOrAdminCanReadDashboard()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));var w=ready(base);String version=w.path("version").path("id").asText();request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(base+"/submissions","GET",null,403);request(base+"/coding-jobs","GET",null,403);request(base+"/workspace/me/submit","POST",Map.of("versionId",version,"revision",request(base,"GET",null,200).path("revision").asLong(),"rulesAccepted",true),403);assertThat(request(base+"/submission/me/versions","GET",null,200)).isEmpty();
 }
 @Test void resubmitPreservesStableRegistrationAndAllImmutableVersionHistory()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH","allowResubmitBeforeDeadline",true));var first=ready(base);var a=submit(base,first);var draft=request(base+"/workspace/me?language=PYTHON","GET",null,200);request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",draft.path("revision").asLong(),"sourceCode","print('second version')"),202);draft=done(base);request(base+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",draft.path("revision").asLong()),202);var b=submit(base,done(base));assertThat(b.path("registrationId")).isEqualTo(a.path("registrationId"));assertThat(b.path("versionId")).isNotEqualTo(a.path("versionId"));assertThat(request(base+"/submission/me/versions","GET",null,200)).hasSize(2);assertThat(request(base,"GET",null,200).path("registrations")).hasSize(1);
 }
 @Test void preuploadedSourceCannotBypassCodeOnlyAndBothUsesSameValidationPipeline()throws Exception{
  String both=cup(Map.of("submissionMode","BOTH","hiddenTestsEnabled",true));var bot=upload("Uploaded participant");var imported=request(both+"/submission/preuploaded","POST",Map.of("botId",bot.id()),200);var w=done(both);assertThat(w.path("version").path("sourceType").asText()).isEqualTo("PRE_UPLOADED");request(both+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);var result=submit(both,done(both));assertThat(result.path("sourceType").asText()).isEqualTo("PRE_UPLOADED");
  String code=cup(Map.of("submissionMode","CODE_IN_PLATFORM_ONLY"));request(code+"/submission/preuploaded","POST",Map.of("botId",bot.id()),400);
 }
 @Test void organizerRetestAndRejectionRequireReasonAndRevisionWithoutExposingSource()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));var w=ready(base);var s=submit(base,w);String action=base+"/submissions/"+s.path("id").asText()+"/action";long revision=request(base,"GET",null,200).path("revision").asLong();request(action,"POST",Map.of("action","reject","reason","","revision",revision),400);request(action,"POST",Map.of("action","reject","reason","review","revision",-1),409);request(action,"POST",Map.of("action","retest","reason","Review required tests","revision",revision),200);assertThat(done(base).path("status").asText()).isEqualTo("READY");
 }
 @Test void deadlineAutopromotionUsesLatestReadyAndThenLocksAtomically()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH","submissionDeadline",java.time.Instant.now().plusSeconds(600).toString(),"deadlineBehavior","AUTO_SUBMIT_LATEST_READY"));var w=ready(base);String id=base.substring(base.lastIndexOf('/')+1);var t=tournaments.find(id);var tree=json.valueToTree(t.botPolicy);((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("submissionDeadline",java.time.Instant.now().minusSeconds(1).toString());t.botPolicy=json.treeToValue(tree,com.rpsbattle.tournament.domain.TournamentBotPolicy.class);submissions.deadlines();assertThat(request(base+"/submission/me","GET",null,200).path("status").asText()).isEqualTo("LOCKED");request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",0,"sourceCode","print('late')"),409);
 }
 @Test void rejectedEntryRequiresExplicitPermissionBeforeAnotherVersionCanReplaceIt()throws Exception{
  String base=cup(Map.of("submissionMode","BOTH"));var first=ready(base);var entry=submit(base,first);String action=base+"/submissions/"+entry.path("id").asText()+"/action";
  request(action,"POST",Map.of("action","reject","reason","Review before replacement","revision",request(base,"GET",null,200).path("revision").asLong()),200);
  var w=request(base+"/workspace/me?language=PYTHON","GET",null,200);request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('replacement')"),202);w=done(base);request(base+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);w=done(base);
  request(base+"/workspace/me/submit","POST",Map.of("versionId",w.path("version").path("id").asText(),"revision",request(base,"GET",null,200).path("revision").asLong(),"rulesAccepted",true),409);
  request(action,"POST",Map.of("action","allow-resubmit","reason","Correction permitted","revision",request(base,"GET",null,200).path("revision").asLong()),200);assertThat(submit(base,w).path("status").asText()).isEqualTo("SUBMITTED");
 }
}
