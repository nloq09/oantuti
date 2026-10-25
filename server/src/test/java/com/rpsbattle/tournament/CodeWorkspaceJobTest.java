package com.rpsbattle.tournament;
import com.rpsbattle.codevalidation.BotTestService;

import com.rpsbattle.tournament.coding.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeWorkspaceJobTest extends StageThreeWebFixture {
 @MockitoBean WorkspaceSandbox sandbox;
 @MockitoBean BotTestService tests;
 String workspace(Map<String,Object> policy)throws Exception{loginAccount();var cup=participantTournament(Map.of("name","Workspace pipeline","botPolicy",policy));String base="/api/tournaments/"+cup.path("id").asText();managerRequest(base+"/open-registration","POST",Map.of(),200);request(base+"/workspace","POST",Map.of("language","PYTHON"),200);return base+"/workspace/me";}
 com.fasterxml.jackson.databind.JsonNode done(String path)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){var w=request(path+"?language=PYTHON","GET",null,200);if(!Set.of("BUILDING","TESTING").contains(w.path("status").asText()))return w;Thread.sleep(20);}throw new AssertionError("Worker did not finish");}
 BotTestService.Result pass(String type){return new BotTestService.Result(true,type,List.of(),10,"COMPLETE",0,0,10,"",Map.of());}
 @Test void jobsUseImmutableSourceBlockConcurrentEditsAndEditsInvalidateReadyVersion()throws Exception{
  String path=workspace(Map.of("submissionMode","BOTH"));var release=new CountDownLatch(1);String source="print('snapshot')\n";
  when(sandbox.build("PYTHON",source)).thenAnswer(invocation->{release.await(5,TimeUnit.SECONDS);return new WorkspaceSandbox.Build(true,UUID.randomUUID(),"a".repeat(64),"Build successful");});
  var queued=request(path+"/build","POST",Map.of("language","PYTHON","revision",0,"sourceCode",source),202);
  try{request(path,"PUT",Map.of("language","PYTHON","revision",queued.path("revision").asLong(),"sourceCode","print('changed')"),409);request(path+"/test","POST",Map.of("language","PYTHON","revision",queued.path("revision").asLong()),409);}finally{release.countDown();}
  var built=done(path);assertThat(built.path("status").asText()).isEqualTo("BUILT");assertThat(built.path("version").path("sourceCode").asText()).isEqualTo(source);
  when(tests.test(any())).thenReturn(pass("PUBLIC"));when(tests.sample(any())).thenReturn(pass("SAMPLE_MATCH"));when(tests.hidden(any())).thenReturn(new BotTestService.HiddenResult(false,List.of(new BotTestService.HiddenCase("Hidden Test 1","FAIL"))));
  request(path+"/test","POST",Map.of("language","PYTHON","revision",built.path("revision").asLong()),202);var tested=done(path);assertThat(tested.path("status").asText()).isEqualTo("BUILT");
  request(path+"/test-match","POST",Map.of("language","PYTHON","revision",tested.path("revision").asLong()),202);var ready=done(path);assertThat(ready.path("status").asText()).isEqualTo("READY");
  var changed=request(path,"PUT",Map.of("language","PYTHON","revision",ready.path("revision").asLong(),"sourceCode","print('new draft')"),200);assertThat(changed.path("status").asText()).isEqualTo("DRAFT");assertThat(changed.path("version").isNull()).isTrue();request(path+"/test","POST",Map.of("language","PYTHON","revision",changed.path("revision").asLong()),400);verify(sandbox).build("PYTHON",source);
 }
 @Test void hiddenRequirementsNeverBecomeReadyFromOnlyPublicTestsAndVersionLimitsApply()throws Exception{
  String path=workspace(Map.of("submissionMode","BOTH","hiddenTestsEnabled",true,"maxSubmissionVersions",1));when(sandbox.build(any(),any())).thenReturn(new WorkspaceSandbox.Build(true,UUID.randomUUID(),"b".repeat(64),"Built"));when(tests.test(any())).thenReturn(pass("PUBLIC"));when(tests.sample(any())).thenReturn(pass("SAMPLE_MATCH"));when(tests.hidden(any())).thenReturn(new BotTestService.HiddenResult(false,List.of(new BotTestService.HiddenCase("Hidden Test 1","FAIL"))));
  request(path+"/build","POST",Map.of("language","PYTHON","revision",0,"sourceCode","print('one')"),202);var w=done(path);
  for(String type:List.of("test","test-match")){request(path+"/"+type,"POST",Map.of("language","PYTHON","revision",w.path("revision").asLong()),202);w=done(path);}
  assertThat(w.path("status").asText()).isEqualTo("BUILT");request(path+"/build","POST",Map.of("language","PYTHON","revision",w.path("revision").asLong(),"sourceCode","print('two')"),400);
 }
 @Test void disabledTestsCannotBeTriggeredThroughDirectApi()throws Exception{
  String path=workspace(Map.of("submissionMode","BOTH","allowPublicTests",false,"allowCustomTestMatch",false));for(String type:List.of("test","test-match"))request(path+"/"+type,"POST",Map.of("language","PYTHON","revision",0),403);verifyNoInteractions(tests);
 }
}
