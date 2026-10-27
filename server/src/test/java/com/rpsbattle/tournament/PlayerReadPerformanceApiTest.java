package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PlayerReadPerformanceApiTest extends StageThreeWebFixture {
    @org.springframework.beans.factory.annotation.Autowired OwnedTournamentService tournamentOwner;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.account.repository.UserRepository users;
    @Test void publicLightSummaryAndPagesRemainBoundedAndPersonalReadsKeepSessionAuthorization() throws Exception {
        loginAccount();var t=tournamentOwner.create(com.rpsbattle.auth.AuthService.sessionAuthentication(account),"Player read API",TournamentFormat.SINGLE_ELIMINATION,"PUBLIC",false,4000,null,null,ParticipantPolicy.HUMAN_ONLY);
        for(int i=0;i<1000;i++) {
            var id=new com.rpsbattle.account.domain.UserId(UUID.randomUUID());var username="page_"+id.value().toString().replace("-","").substring(0,20);var now=java.time.Instant.now();
            users.save(new com.rpsbattle.account.domain.User(id,username,username+"@example.test",account.passwordHash(),"Public participant "+i,account.role(),account.status(),now,now,null));
            t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),null,null,"Public participant "+i,i,id.value()));
        }
        tournaments.changed(t);
        String base="/api/tournaments/"+t.id;
        var light=request(base+"/summary?light=true","GET",null,200);assertThat(light.path("participantCount").asInt()).isEqualTo(1000);assertThat(light.path("participants").size()).isZero();assertThat(light.path("ranking").size()).isZero();assertThat(light.toString().length()).isLessThan(20_000);
        var page=request(base+"/player-pages/participants?page=1&size=20","GET",null,200);assertThat(page.path("total").asInt()).isEqualTo(1000);assertThat(page.path("items").size()).isEqualTo(20);assertThat(page.path("items").get(0).path("displayName").asText()).isEqualTo("Public participant 20");assertThat(page.toString()).doesNotContain("password","email","ownerUserId","botToken");
        var ranking=request(base+"/player-pages/ranking?page=1&size=20","GET",null,200);assertThat(ranking.path("items").get(0).path("rank").asInt()).isEqualTo(21);
        request(base+"/player-pages/participants?size=1000","GET",null,400);request(base+"/player-pages/source","GET",null,400);request(base+"/player-extras/private","GET",null,400);request(base+"/player-extras/teams","GET",null,400);
        var me=request(base+"/me","GET",null,200);assertThat(me.path("states").size()).isZero();request("/api/auth/logout","POST",Map.of(),200);request(base+"/me","GET",null,401);request(base+"/summary?light=true","GET",null,200);request(base+"/player-pages/participants","GET",null,200);
    }
}
