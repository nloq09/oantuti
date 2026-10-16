package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentBotPolicyApiTest extends StageThreeWebFixture {
 @Test void creationPublicReadOwnerOnlyEditsAndLegacyRoutesEnforcePolicy()throws Exception{
  loginAccount();var owner=account;var policy=Map.of("submissionMode","CODE_IN_PLATFORM_ONLY","allowedLanguages",List.of("CPP","PYTHON"));
  var cup=request("/api/tournaments","POST",Map.of("name","Coding policy","botPolicy",policy),201);String path="/api/tournaments/"+cup.path("id").asText();assertThat(cup.path("botPolicy").path("submissionMode").asText()).isEqualTo("CODE_IN_PLATFORM_ONLY");
  var bot=upload("Forbidden preuploaded");request(path+"/participants","POST",Map.of("botId",bot.id()),400);var read=request(path+"/bot-policy","GET",null,200);assertThat(read.path("policy").has("sourceCode")).isFalse();
  request(path+"/bot-policy","PUT",Map.of("revision",-1,"policy",policy),409);
  request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(path+"/bot-policy","PUT",Map.of("revision",cup.path("revision").asLong(),"policy",policy),403);
  request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);account=owner;request(path+"/open-registration","POST",Map.of(),200);request(path+"/registrations","POST",Map.of("botId",bot.id()),400);request(path+"/bot-policy","PUT",Map.of("revision",request(path,"GET",null,200).path("revision").asLong(),"policy",policy),400);
 }
}
