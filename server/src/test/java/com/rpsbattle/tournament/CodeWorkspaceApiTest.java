package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CodeWorkspaceApiTest extends StageThreeWebFixture {
 String cup(Map<String,Object> policy)throws Exception{var t=participantTournament(Map.of("name","Workspace test","botPolicy",policy));String base="/api/tournaments/"+t.path("id").asText();managerRequest(base+"/open-registration","POST",Map.of(),200);return base;}
 @Test void serverDraftsArePrivateAcrossAccountsAndOptimisticRevisionsProtectEdits()throws Exception{
  loginAccount();var owner=account;String base=cup(Map.of("submissionMode","CODE_IN_PLATFORM_ONLY","allowedLanguages",List.of("PYTHON","JAVA"))),path=base+"/workspace";
  var first=request(path,"POST",Map.of("language","PYTHON"),200);assertThat(first.path("sourceCode").asText()).contains("legalActions");
  String privateCode="print('private-source-owner')\n";var saved=request(path+"/me","PUT",Map.of("language","PYTHON","revision",first.path("revision").asLong(),"sourceCode",privateCode),200);
  request(path+"/me","PUT",Map.of("language","PYTHON","revision",0,"sourceCode","print('stale')"),409);
  assertThat(request(path+"/me?language=PYTHON","GET",null,200).path("sourceCode").asText()).isEqualTo(privateCode);
  assertThat(request(base,"GET",null,200).toString()).doesNotContain("private-source-owner");
  request(path,"POST",Map.of("language","CPP"),400);request(path,"POST",Map.of("language","JAR"),400);
  request("/api/auth/logout","POST",Map.of(),200);account=null;request(path+"/me?language=PYTHON","GET",null,401);loginAccount();
  request(path+"/me?language=PYTHON","GET",null,400);var own=request(path,"POST",Map.of("language","PYTHON","userId",owner.id()),200);assertThat(own.path("id").asText()).isNotEqualTo(saved.path("id").asText());assertThat(own.path("sourceCode").asText()).doesNotContain("private-source-owner");
  request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);account=owner;
  var java=request(path,"POST",Map.of("language","JAVA"),200);request(path+"/me","PUT",Map.of("language","JAVA","revision",java.path("revision").asLong(),"sourceCode","package bad; class Main{}"),400);
  managerRequest(base+"/cancel","POST",Map.of(),200);request(path+"/me","PUT",Map.of("language","PYTHON","revision",saved.path("revision").asLong(),"sourceCode",privateCode),409);assertThat(request(path+"/me?language=PYTHON","GET",null,200).path("sourceCode").asText()).isEqualTo(privateCode);
 }
 @Test void sourceModeDeadlineAndDisabledDraftPolicyAreEnforcedOnServer()throws Exception{
  loginAccount();String pre=cup(Map.of("submissionMode","PRE_UPLOADED_ONLY"));request(pre+"/workspace","POST",Map.of("language","PYTHON"),403);
  String future=cup(Map.of("submissionMode","BOTH","submissionOpenAt",java.time.Instant.now().plusSeconds(3600).toString()));request(future+"/workspace","POST",Map.of("language","PYTHON"),409);
  String expired=cup(Map.of("submissionMode","BOTH","submissionDeadline",java.time.Instant.now().minusSeconds(60).toString()));request(expired+"/workspace","POST",Map.of("language","PYTHON"),409);
  String noSave=cup(Map.of("submissionMode","BOTH","allowDraftSave",false));request(noSave+"/workspace","POST",Map.of("language","PYTHON"),200);request(noSave+"/workspace/me","PUT",Map.of("language","PYTHON","revision",0,"sourceCode","print('x')"),403);
 }
}
