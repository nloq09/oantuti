package com.rpsbattle.tournament;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TournamentLifecycleApiTest extends StageThreeWebFixture {
    @Test void organizerCancellationDecisionRequiresCurrentRevisionAndPersistsReason() throws Exception {
        loginAccount();var cup=request("/api/tournaments","POST",Map.of("name","Cancel decision"),201);String base="/api/tournaments/"+cup.path("id").asText();long revision=cup.path("revision").asLong();
        request(base+"/cancel","POST",Map.of("revision",revision+1,"reason","Schedule conflict"),409);
        request(base+"/cancel","POST",Map.of("revision",revision,"reason","  "),400);
        assertThat(request(base,"GET",null,200).path("status").asText()).isEqualTo("DRAFT");
        var cancelled=request(base+"/cancel","POST",Map.of("revision",revision,"reason","  Schedule conflict  "),200);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");var audit=cancelled.path("audit");assertThat(audit.get(audit.size()-1).path("reason").asText()).isEqualTo("Schedule conflict");
        var legacy=request("/api/tournaments","POST",Map.of("name","Legacy cancellation"),201);request("/api/tournaments/"+legacy.path("id").asText()+"/cancel","POST",Map.of(),200);
    }
    @Test void cancellationMetadataCannotGrantAnotherAccountOrganizerRights() throws Exception {
        loginAccount();var cup=request("/api/tournaments","POST",Map.of("name","Protected cancellation"),201);String base="/api/tournaments/"+cup.path("id").asText();request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();
        request(base+"/cancel","POST",Map.of("revision",cup.path("revision").asLong(),"reason","Not the organizer","role","ADMIN"),403);
        assertThat(request(base,"GET",null,200).path("status").asText()).isEqualTo("DRAFT");
    }
    @Test void registrationCanOpenCloseAndLockRosterWithoutStartingAnyMatch() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(uploads.available(com.rpsbattle.bot.upload.BotLanguage.PYTHON));
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Lifecycle API"),201);
        String path="/api/tournaments/"+created.path("id").asText();
        var opened=request(path+"/open-registration","POST",Map.of(),200);
        assertThat(opened.path("status").asText()).isEqualTo("REGISTRATION");assertThat(opened.path("startedAt").isNull()).isTrue();
        assertThat(opened.path("statusChangedAt").asText()).isNotBlank();
        request(path+"/open-registration","POST",Map.of(),400);request(path+"/close-registration","POST",Map.of(),400);
        var a=upload("Lifecycle A");var b=upload("Lifecycle B");
        request(path+"/participants","POST",Map.of("botId",a.id()),200);request(path+"/participants","POST",Map.of("botId",b.id()),200);
        for(var r:request(path,"GET",null,200).path("registrations"))request(path+"/registrations/"+r.path("id").asText()+"/approve","POST",Map.of(),200);
        request(path+"/start","POST",Map.of(),400);
        var ready=request(path+"/close-registration","POST",Map.of(),200);
        assertThat(ready.path("status").asText()).isEqualTo("READY");assertThat(ready.path("matches").size()).isZero();assertThat(ready.path("startedAt").isNull()).isTrue();
        request(path+"/participants","POST",Map.of("botId",upload("Locked").id()),400);
        request(path+"/participants/"+ready.path("participants").get(0).path("participantId").asText(),"DELETE",null,400);
        request(path+"/open-registration","POST",Map.of(),400);
        var cancelled=request(path+"/cancel","POST",Map.of(),200);assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("finishedAt").isNull()).isFalse();request(path+"/start","POST",Map.of(),400);
    }
    @Test void strangerAndGuestCannotControlRegistrationEvenWithSpoofedCapabilities() throws Exception {
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Lifecycle protected"),201);
        String path="/api/tournaments/"+created.path("id").asText();
        request("/api/auth/logout","POST",Map.of(),200);
        request(path+"/open-registration","POST",Map.of(),401);
        account=null;loginAccount();
        request(path+"/open-registration","POST",Map.of("ownerUserId",created.path("ownerUserId").asText()),403,Map.of("X-Tournament-Token","spoof"));
        request(path+"/close-registration","POST",Map.of(),403);
        assertThat(request(path,"GET",null,200).path("status").asText()).isEqualTo("DRAFT");
    }
}
