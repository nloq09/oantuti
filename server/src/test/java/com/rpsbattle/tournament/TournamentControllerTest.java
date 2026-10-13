package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TournamentControllerTest extends StageThreeWebFixture {
    @Test void createAddAuthorizeRemoveExportAndInvalidStart() throws Exception {
        var t=request("/api/tournaments","POST",Map.of("name","API Championship","format","ROUND_ROBIN"),201);String path="/api/tournaments/"+t.path("id").asText();
        var owner=Map.of("X-Tournament-Token",t.path("ownerToken").asText());
        request(path+"/start","POST",Map.of(),400,owner);var bot=upload("API Bot");
        request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken","wrong"),400);
        var added=request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token(),"displayName","Alpha"),200);
        assertThat(added.toString()).doesNotContain(bot.token(),"botToken");assertThat(added.path("participants").size()).isEqualTo(1);
        request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        assertThat(request(path+"/ranking","GET",null,200).get(0).path("points").asInt()).isZero();
        assertThat(request(path+"/export","GET",null,200).toString()).doesNotContain(bot.token());
        request(path+"/participants/"+added.path("participants").get(0).path("participantId").asText(),"DELETE",null,200,owner);
        request(path+"/cancel","POST",Map.of(),200,owner);request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        request("/api/tournaments/missing","GET",null,404);
    }
    @Test void publicRegistrationAndOwnerPermissionsAreSeparate() throws Exception {
        var t=request("/api/tournaments","POST",Map.of("name","Open Cup","format","ROUND_ROBIN"),201);
        String path="/api/tournaments/"+t.path("id").asText(), secret=t.path("ownerToken").asText();
        assertThat(secret).isNotBlank();
        assertThat(t.path("participants").size()).isZero();
        var owner=Map.of("X-Tournament-Token",secret);
        request(path+"/start","POST",Map.of(),403);
        request(path+"/cancel","POST",Map.of(),403,Map.of("X-Tournament-Token","wrong"));
        var a=upload("Guest A");var b=upload("Guest B");
        var first=request(path+"/participants","POST",Map.of("botId",a.id(),"botToken",a.token()),200);
        var second=request(path+"/participants","POST",Map.of("botId",b.id(),"botToken",b.token()),200);
        assertThat(second.path("participants").size()).isEqualTo(2);
        String participant=first.path("participants").get(0).path("participantId").asText();
        request(path+"/participants/"+participant,"DELETE",null,403);
        request(path+"/participants/"+participant,"DELETE",null,403,Map.of("X-Bot-Token",b.token()));
        assertThat(request(path,"GET",null,200).toString()).doesNotContain(secret,a.token(),b.token(),"ownerToken","botToken");
        assertThat(request("/api/tournaments","GET",null,200).toString()).doesNotContain(secret);
        assertThat(request(path+"/export","GET",null,200).toString()).doesNotContain(secret);
        var withdrawn=request(path+"/participants/"+participant,"DELETE",null,200,Map.of("X-Bot-Token",a.token()));
        assertThat(withdrawn.path("participants").size()).isEqualTo(1);
        String remaining=withdrawn.path("participants").get(0).path("participantId").asText();
        request(path+"/participants/"+remaining,"DELETE",null,200,owner);
        request(path+"/cancel","POST",Map.of(),200,owner);
        request(path+"/participants","POST",Map.of("botId",a.id(),"botToken",a.token()),400);
    }
}
