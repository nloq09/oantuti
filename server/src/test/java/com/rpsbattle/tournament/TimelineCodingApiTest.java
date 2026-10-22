package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.codevalidation.BotTestService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.test.context.TestPropertySource(properties="rps.coding.deadline-initial-delay-ms=3600000")
// Deadline cases advance the clock and invoke processDeadlines explicitly.
// A background deadline tick must not consume Mockito stubs while setup installs them.
@org.springframework.test.context.TestPropertySource(properties="rps.coding.deadline-initial-delay-ms=3600000")
class TimelineCodingApiTest extends StageThreeWebFixture {
 // Each test stubs START again; keep a valid clock between tests and during scheduler shutdown.
 @MockitoBean(reset=org.springframework.test.context.bean.override.mockito.MockReset.NONE) Clock clock;
 @MockitoBean WorkspaceSandbox sandbox;
 @MockitoBean BotTestService tests;
 static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
 // Schedulers share these mocks: install stubs without invoking the previous test's live answer.
 @BeforeEach void setup(){doReturn(START.plusSeconds(30)).when(clock).instant();doReturn(ZoneOffset.UTC).when(clock).getZone();doReturn(new WorkspaceSandbox.Build(true,UUID.randomUUID(),"a".repeat(64),"Build successful")).when(sandbox).build(any(),any());var pass=new BotTestService.Result(true,"PUBLIC",List.of(),8,"PASS",0,0,1,"",Map.of());doReturn(pass).when(tests).test(any());doReturn(pass).when(tests).sample(any());doReturn(new BotTestService.HiddenResult(true,List.of(new BotTestService.HiddenCase("Hidden Test 1","PASS")))).when(tests).hidden(any());}
 String cup(String mode,boolean approve)throws Exception{loginAccount();var t=request("/api/tournaments","POST",Map.of("name","Coding window","botPolicy",Map.of("submissionMode",mode,"submissionOpenAt",START.minusSeconds(60),"submissionDeadline",START.plusSeconds(60),"hiddenTestsEnabled",true)),201);String base="/api/tournaments/"+t.path("id").asText();var timeline=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(190),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",timeline),200);request(base+"/open-registration","POST",Map.of(),200);var registration=request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","PYTHON"),200);if(approve)request(base+"/participant-applications/"+registration.path("registration").path("id").asText()+"/review","POST",Map.of("revision",request(base,"GET",null,200).path("revision").asLong(),"decision","APPROVED","reason","Coding approval"),200);return base;}
 com.fasterxml.jackson.databind.JsonNode done(String base)throws Exception{long until=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(System.nanoTime()<until){var w=request(base+"/workspace/me?language=PYTHON","GET",null,200);if(!Set.of("BUILDING","TESTING").contains(w.path("status").asText()))return w;Thread.sleep(25);}throw new AssertionError("Coding job did not finish");}
 @Test void exactCodingOpenRequiresApprovalAndIgnoresConflictingLegacySubmissionDeadline()throws Exception{
  String base=cup("CODE_IN_PLATFORM_ONLY",true);when(clock.instant()).thenReturn(START.plusSeconds(120).minusNanos(1));request(base+"/workspace","POST",Map.of("language","PYTHON"),409);when(clock.instant()).thenReturn(START.plusSeconds(120));var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("canAccessWorkspace").asBoolean()).isTrue();assertThat(flow.path("canModifyWorkspace").asBoolean()).isTrue();assertThat(flow.path("codingCloseAt").asText()).isEqualTo(START.plusSeconds(180).toString());
  var w=request(base+"/workspace","POST",Map.of("language","PYTHON"),200);request(base+"/workspace/me","PUT",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('saved within coding')\n"),200);
 }
 @Test void exactClosePreservesSourceForReadingButRejectsAllMutationsAndLateReconnect()throws Exception{
  String base=cup("BOTH",true);when(clock.instant()).thenReturn(START.plusSeconds(120));var w=request(base+"/workspace","POST",Map.of("language","PYTHON"),200);w=request(base+"/workspace/me","PUT",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('saved source')\n"),200);when(clock.instant()).thenReturn(START.plusSeconds(180));
  var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("canModifyWorkspace").asBoolean()).isFalse();assertThat(flow.path("canAccessWorkspace").asBoolean()).isTrue();assertThat(request(base+"/workspace/me?language=PYTHON","GET",null,200).path("sourceCode").asText()).contains("saved source");
  request(base+"/workspace/me","PUT",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('late replacement')"),409);request(base+"/workspace","POST",Map.of("language","CPP"),409);
  for(String job:List.of("build","test","test-match","validate"))request(base+"/workspace/me/"+job,"POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","late"),409);
  request(base+"/submission/preuploaded","POST",Map.of("botId",UUID.randomUUID().toString()),409);request(base+"/workspace/me/submit","POST",Map.of("versionId",UUID.randomUUID().toString(),"rulesAccepted",true,"revision",request(base,"GET",null,200).path("revision").asLong()),409);assertThat(request(base+"/workspace/me?language=PYTHON","GET",null,200).path("sourceCode").asText()).doesNotContain("replacement");
 }
 @Test void buildValidateAndSubmitUseTheTimelineWindowAndPreserveApplicationApproval()throws Exception{
  String base=cup("CODE_IN_PLATFORM_ONLY",true);when(clock.instant()).thenReturn(START.plusSeconds(120));var w=request(base+"/workspace","POST",Map.of("language","PYTHON"),200);request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('source snapshot')\n"),202);w=done(base);assertThat(w.path("status").asText()).isEqualTo("BUILT");request(base+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);w=done(base);assertThat(w.path("status").asText()).isEqualTo("READY");request(base+"/workspace/me/submit","POST",Map.of("versionId",w.path("version").path("id").asText(),"rulesAccepted",true,"revision",request(base,"GET",null,200).path("revision").asLong()),200);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("APPROVED");
 }
 @Test void preuploadedOnlyCanImportAndValidateButCannotCreateEditorOrBuildSource()throws Exception{
  String base=cup("PRE_UPLOADED_ONLY",true);var bot=upload("Imported coding bot");when(clock.instant()).thenReturn(START.plusSeconds(120));request(base+"/workspace","POST",Map.of("language","PYTHON"),403);request(base+"/submission/preuploaded","POST",Map.of("botId",bot.id()),200);var w=done(base);assertThat(w.path("version").path("sourceType").asText()).isEqualTo("PRE_UPLOADED");request(base+"/workspace/me/validate","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);assertThat(done(base).path("status").asText()).isEqualTo("READY");request(base+"/workspace/me/build","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('forged code')"),403);
 }
 @Test void pendingParticipantCannotReadOrCreateDuringCodingAndOtherUsersCannotReadSource()throws Exception{
  String base=cup("BOTH",false);when(clock.instant()).thenReturn(START.plusSeconds(120));request(base+"/workspace","POST",Map.of("language","PYTHON"),403);request(base+"/workspace/me?language=PYTHON","GET",null,403);request(base+"/submission/me/versions","GET",null,403);assertThat(request(base+"/participant-flow","GET",null,200).path("canAccessWorkspace").asBoolean()).isFalse();
 }
 @Test void participantDashboardIncludesApprovedBeforeWorkspaceAndRejectsInvalidFilters()throws Exception{
  String base=cup("BOTH",true);var data=request(base+"/submissions?view=participants","GET",null,200);assertThat(data.path("summary").path("approved").asInt()).isEqualTo(1);assertThat(data.path("items")).hasSize(1);assertThat(data.path("items").get(0).path("workspace_state").asText()).isEqualTo("NO_WORKSPACE");request(base+"/submissions?view=participants&filter=INVALID","GET",null,400);
 }
}
