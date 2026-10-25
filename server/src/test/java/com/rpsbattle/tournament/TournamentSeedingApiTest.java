package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentSeedingApiTest extends StageThreeWebFixture {
    @Test void manualConfigurationIsAuthorizedLockedAtReadyAndTamperedFrozenBotCannotStart() throws Exception {
        loginAccount();var owner=account;var a=upload("Seed A");var b=upload("Seed B");
        String path="/api/tournaments/"+participantTournament(Map.of("name","Seeds")).path("id").asText();
        managerRequest(path+"/open-registration","POST",Map.of(),200);request(path+"/participants","POST",Map.of("botId",a.id()),200);var pending=request(path+"/participants","POST",Map.of("botId",b.id()),200);
        for(var r:pending.path("registrations"))managerRequest(path+"/registrations/"+r.path("id").asText()+"/approve","POST",Map.of(),200);var cup=request(path,"GET",null,200);
        String first=cup.path("participants").get(0).path("participantId").asText(),second=cup.path("participants").get(1).path("participantId").asText();
        managerRequest(path+"/seeding","POST",Map.of("strategy","MANUAL","manualSeeds",Map.of(first,1,second,1)),400);
        managerRequest(path+"/seeding","POST",Map.of("strategy","MANUAL","manualSeeds",Map.of(first,1)),400);
        managerRequest(path+"/seeding","POST",Map.of("strategy","MANUAL","seed","9223372036854775807","manualSeeds",Map.of(first,2,second,1)),200);
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(path+"/seeding","POST",Map.of("strategy","RANDOM"),403);
        account=owner;request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);
        var ready=managerRequest(path+"/close-registration","POST",Map.of(),200);
        assertThat(ready.path("seedingSeed").asText()).isEqualTo("9223372036854775807");assertThat(ready.path("participants").get(0).path("participantId").asText()).isEqualTo(second);
        assertThat(ready.path("participants").get(0).path("seedNumber").asInt()).isEqualTo(1);managerRequest(path+"/seeding","POST",Map.of("strategy","RANKING"),400);
        java.nio.file.Files.writeString(uploads.source(a),"print('different source')");
        var r=ready.path("registrations").get(0);String bot=r.path("botId").asText(),hash=r.path("artifactHash").asText();assertThat(hash).matches("[a-f0-9]{64}");
        var frozen=uploads.source(uploads.stored(bot)).getParent().resolve("versions").resolve(hash).resolve("main.py");java.nio.file.Files.writeString(frozen,"tampered");
        assertThat(managerRequest(path+"/start","POST",Map.of(),400).path("error").asText()).isEqualTo("BOT_ARTIFACT_CHANGED");
        var unchanged=request(path,"GET",null,200);assertThat(unchanged.path("status").asText()).isEqualTo("READY");assertThat(unchanged.path("matches").size()).isZero();
    }
}
