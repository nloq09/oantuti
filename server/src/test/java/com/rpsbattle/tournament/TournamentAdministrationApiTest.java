package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentAdministrationApiTest extends StageThreeWebFixture {
    @Test void strangersCannotUseAdministrationAndHistoryFiltersUseSessionIdentity() throws Exception {
        loginAccount();var owner=account;var cup=request("/api/tournaments","POST",Map.of("name","Admin API"),201);String path="/api/tournaments/"+cup.path("id").asText();long revision=cup.path("revision").asLong();
        var configured=request(path+"/round-policy","POST",Map.of("revision",revision,"autoAdvanceRounds",false),200);assertThat(configured.path("autoAdvanceRounds").asBoolean()).isFalse();
        request(path+"/round-policy","POST",Map.of("revision",revision,"autoAdvanceRounds",true),409);
        assertThat(request("/api/account/tournaments?filter=created","GET",null,200).toString()).contains(cup.path("id").asText());
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();
        request(path+"/review","GET",null,403);request(path+"/control/pause","POST",Map.of("revision",configured.path("revision").asLong()),403);
        request(path+"/participants/unknown/status","POST",Map.of("revision",configured.path("revision").asLong(),"status","DISQUALIFIED","reason","test"),403);
        assertThat(request("/api/account/tournaments?filter=created","GET",null,200).toString()).doesNotContain(cup.path("id").asText());
        request("/api/account/tournaments?filter=invalid","GET",null,400);assertThat(request(path+"/report","GET",null,200).path("totalMatches").asInt()).isZero();
        assertThat(request(path,"GET",null,200).path("audit").get(0).path("actor").asText()).isEqualTo(owner.id().value().toString());
    }
    @Test void websocketSendsVersionedLifecycleNotificationsAlongsideAuthoritativeSnapshot() throws Exception {
        loginAccount();var cup=request("/api/tournaments","POST",Map.of("name","Events"),201);String id=cup.path("id").asText();
        try(var watcher=new Peer()) {watcher.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",id));watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE"));
            request("/api/tournaments/"+id+"/open-registration","POST",Map.of(),200);
            var event=watcher.await(m->m.path("payload").path("event").path("type").asText().equals("REGISTRATION_OPENED"));assertThat(event.path("payload").path("revision").asLong()).isGreaterThan(cup.path("revision").asLong());assertThat(event.toString()).doesNotContain("password","botToken");
        }
    }
}
