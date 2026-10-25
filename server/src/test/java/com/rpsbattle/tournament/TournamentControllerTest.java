package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TournamentControllerTest extends StageThreeWebFixture {
    @org.junit.jupiter.api.BeforeEach void accountLogin() throws Exception {loginAccount();}
    @Test void createAddAuthorizeRemoveExportAndInvalidStart() throws Exception {
        var t=participantTournament(Map.of("name","API Championship","format","ROUND_ROBIN"));String path="/api/tournaments/"+t.path("id").asText();
        managerRequest(path+"/start","POST",Map.of(),400);var bot=upload("API Bot");
        managerRequest(path+"/open-registration","POST",Map.of(),200);
        // Client tokens are ignored; the current account owns the bot.
        var pending=request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token(),"displayName","Alpha"),200);
        var added=managerRequest(path+"/registrations/"+pending.path("registrations").get(0).path("id").asText()+"/approve","POST",Map.of(),200);
        assertThat(added.toString()).doesNotContain(bot.token(),"botToken");assertThat(added.path("participants").size()).isEqualTo(1);
        request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        assertThat(request(path+"/ranking","GET",null,200).get(0).path("points").asInt()).isZero();
        assertThat(request(path+"/export","GET",null,200).toString()).doesNotContain(bot.token());
        managerRequest(path+"/participants/"+added.path("participants").get(0).path("participantId").asText(),"DELETE",null,200);
        managerRequest(path+"/cancel","POST",Map.of(),200);request(path+"/participants","POST",Map.of("botId",bot.id(),"botToken",bot.token()),400);
        request("/api/tournaments/missing","GET",null,404);
    }
    @Test void accountRegistrationAndOwnerPermissionsAreSeparate() throws Exception {
        var t=participantTournament(Map.of("name","Open Cup","format","ROUND_ROBIN"));
        String path="/api/tournaments/"+t.path("id").asText(), secret=t.path("ownerToken").asText();
        assertThat(t.has("ownerToken")).isFalse();
        assertThat(t.path("participants").size()).isZero();
        managerRequest(path+"/start","POST",Map.of(),400);
        managerRequest(path+"/open-registration","POST",Map.of(),200);
        // Capability headers are ignored; account ownership is authoritative.
        var a=upload("Guest A");var b=upload("Guest B");
        var pending=request(path+"/participants","POST",Map.of("botId",a.id(),"botToken",a.token()),200);
        var first=managerRequest(path+"/registrations/"+pending.path("registrations").get(0).path("id").asText()+"/approve","POST",Map.of(),200);
        pending=request(path+"/participants","POST",Map.of("botId",b.id(),"botToken",b.token()),200);
        var second=managerRequest(path+"/registrations/"+pending.path("registrations").get(1).path("id").asText()+"/approve","POST",Map.of(),200);
        assertThat(second.path("participants").size()).isEqualTo(2);
        String participant=first.path("participants").get(0).path("participantId").asText();

        assertThat(request(path,"GET",null,200).toString()).doesNotContain(a.token(),b.token(),"ownerToken","botToken");
        assertThat(request("/api/tournaments","GET",null,200).toString()).doesNotContain("ownerToken");
        assertThat(request(path+"/export","GET",null,200).toString()).doesNotContain("ownerToken");
        var withdrawn=request(path+"/participants/"+participant,"DELETE",null,200,Map.of("X-Bot-Token",a.token()));
        assertThat(withdrawn.path("participants").size()).isEqualTo(1);
        String remaining=withdrawn.path("participants").get(0).path("participantId").asText();
        managerRequest(path+"/participants/"+remaining,"DELETE",null,200);
        managerRequest(path+"/cancel","POST",Map.of(),200);
        request(path+"/participants","POST",Map.of("botId",a.id(),"botToken",a.token()),400);
    }
    @Test void cookieAuthenticatedBotRequestsWithoutCsrfAreRejectedOverRealHttp() throws Exception {
        var bot=upload("CSRF Bot");
        for(var path:java.util.List.of("/api/bots","/api/matches","/api/tournaments/cup/participants")) {
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("mode","HUMAN_VS_BOT","botId",bot.id())))).build();
            var response=accountHttp.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
            assertThat(json.readTree(response.body()).path("error").asText()).isEqualTo("CSRF_INVALID");
        }
    }

}
