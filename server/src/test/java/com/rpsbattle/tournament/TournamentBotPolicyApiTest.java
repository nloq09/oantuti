package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentBotPolicyApiTest extends StageThreeWebFixture {
 @Test void creationPublicReadOwnerOnlyEditsAndLegacyRoutesEnforcePolicy()throws Exception{
  loginAccount();var policy=Map.of("submissionMode","CODE_IN_PLATFORM_ONLY","allowedLanguages",List.of("CPP","PYTHON"));
  var cup=participantTournament(Map.of("name","Coding policy","botPolicy",policy));String path="/api/tournaments/"+cup.path("id").asText();assertThat(cup.path("botPolicy").path("submissionMode").asText()).isEqualTo("CODE_IN_PLATFORM_ONLY");
  var bot=upload("Forbidden preuploaded");request(path+"/participants","POST",Map.of("botId",bot.id()),400);var read=request(path+"/bot-policy","GET",null,200);assertThat(read.path("policy").has("sourceCode")).isFalse();
  managerRequest(path+"/bot-policy","PUT",Map.of("revision",-1,"policy",policy),409);
  request(path+"/bot-policy","PUT",Map.of("revision",cup.path("revision").asLong(),"policy",policy),403);
  managerRequest(path+"/open-registration","POST",Map.of(),200);request(path+"/registrations","POST",Map.of("botId",bot.id()),400);managerRequest(path+"/registrations","POST",Map.of("botId",bot.id()),403);managerRequest(path+"/bot-policy","PUT",Map.of("revision",request(path,"GET",null,200).path("revision").asLong(),"policy",policy),400);
 }
}
