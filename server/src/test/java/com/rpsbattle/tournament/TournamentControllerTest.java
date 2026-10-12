package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TournamentControllerTest extends StageThreeWebFixture {
    @Test void createAddAuthorizeRemoveExportAndInvalidStart() throws Exception {
        var t=request("/api/tournaments","POST",Map.of("name","API Championship","format","ROUND_ROBIN"),201);String path="/api/tournaments/"+t.path("id").asText();
        request(path+"/start","POST",Map.of(),400);var bot=upload("API Bot");
        request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken","wrong"),400);
        var added=request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token(),"displayName","Alpha"),200);
        assertThat(added.toString()).doesNotContain(bot.token(),"botToken");assertThat(added.path("participants").size()).isEqualTo(1);
        request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        assertThat(request(path+"/ranking","GET",null,200).get(0).path("points").asInt()).isZero();
        assertThat(request(path+"/export","GET",null,200).toString()).doesNotContain(bot.token());
        request(path+"/participants/"+added.path("participants").get(0).path("participantId").asText(),"DELETE",null,200);
        request(path+"/cancel","POST",Map.of(),200);request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        request("/api/tournaments/missing","GET",null,404);
    }
}
