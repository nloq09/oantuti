package com.rpsbattle.tournament;
import com.rpsbattle.account.domain.User;
import com.rpsbattle.account.domain.UserRole;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentRulesApiTest extends StageThreeWebFixture {
    @Autowired UserRepository users;
    @Autowired TournamentRegistry registry;

    @Test void createKnockoutWith32SlotsAndOwnerTieBreakRulesLockAtReady() throws Exception {
        loginAccount();var owner=account;
        var cup=request("/api/tournaments","POST",Map.of("name","Knockout","format","SINGLE_ELIMINATION","maxParticipants",32),201);
        assertThat(cup.path("format").asText()).isEqualTo("SINGLE_ELIMINATION");assertThat(cup.path("maxParticipants").asInt()).isEqualTo(32);
        assertThat(cup.path("maxConcurrentMatches").asInt()).isEqualTo(4);
        String path="/api/tournaments/"+cup.path("id").asText();
        var rules=Map.of("tieBreakOrder",List.of("POINTS","AVERAGE_MOVE_TIME","HEAD_TO_HEAD"));
        var saved=request(path+"/rules","POST",rules,200);
        assertThat(saved.path("tieBreakOrder").get(1).asText()).isEqualTo("AVERAGE_MOVE_TIME");assertThat(saved.path("maxConcurrentMatches").asInt()).isEqualTo(4);
        request(path+"/rules","POST",Map.of("tieBreakOrder",List.of("WINS","POINTS")),400);
        request(path+"/rules","POST",Map.of("tieBreakOrder",List.of("POINTS","WINS","WINS")),400);
        request("/api/tournaments","POST",Map.of("name","Invalid","maxParticipants",33),400);
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(path+"/rules","POST",rules,403);
        account=owner;request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);
        request(path+"/participants","POST",Map.of("botId",upload("Rules A").id()),200);request(path+"/participants","POST",Map.of("botId",upload("Rules B").id()),200);
        request(path+"/open-registration","POST",Map.of(),200);request(path+"/close-registration","POST",Map.of(),200);
        request(path+"/rules","POST",rules,400);assertThat(request(path,"GET",null,200).path("maxConcurrentMatches").asInt()).isEqualTo(4);
    }

    @Test void userCannotSupplyRunnerCapacityEvenWhenOwningTheTournament() throws Exception {
        loginAccount();int before=registry.findAll().size();
        for(int capacity:List.of(0,1,4,100))request("/api/tournaments","POST",Map.of("name","Forbidden capacity","maxConcurrentMatches",capacity,"role","ADMIN"),403);
        assertThat(registry.findAll()).hasSize(before);
        var cup=request("/api/tournaments","POST",Map.of("name","Server capacity"),201);
        String id=cup.path("id").asText(),path="/api/tournaments/"+id;long revision=cup.path("revision").asLong();
        for(int capacity:List.of(0,1,4,100)){
            request(path+"/rules","POST",Map.of("maxConcurrentMatches",capacity,"tieBreakOrder",List.of("POINTS","WINS")),403);
            var unchanged=request(path,"GET",null,200);assertThat(unchanged.path("revision").asLong()).isEqualTo(revision);assertThat(unchanged.path("maxConcurrentMatches").asInt()).isEqualTo(4);
        }
        request("/api/admin/tournaments/"+id+"/worker-policy","POST",Map.of("revision",0,"mode","LOCAL","minimumTrust","TRUSTED","maxMatches",1,"reason","Capacity change"),403);
        request("/api/admin/workers/"+UUID.randomUUID()+"/capacity","POST",Map.of("revision",0,"maxMatches",1,"reason","Capacity change"),403);
    }

    @Test void adminCanConfigureCapacityAndOwnerTieBreakUpdatesPreserveIt() throws Exception {
        loginAccount();var owner=account;
        var cup=request("/api/tournaments","POST",Map.of("name","Admin-managed capacity"),201);
        String path="/api/tournaments/"+cup.path("id").asText();
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();var u=account;
        account=users.save(new User(u.id(),u.username(),u.email(),u.passwordHash(),u.displayName(),UserRole.ADMIN,u.status(),u.createdAt(),u.updatedAt(),u.lastLoginAt(),u.authVersion()));
        request("/api/auth/logout","POST",Map.of(),200);request("/api/auth/login","POST",Map.of("username",account.username(),"password","Tournament Test Password"),200);
        var configured=request(path+"/rules","POST",Map.of("maxConcurrentMatches",2),200);
        assertThat(configured.path("maxConcurrentMatches").asInt()).isEqualTo(2);long revision=configured.path("revision").asLong();
        for(int capacity:List.of(0,5))request(path+"/rules","POST",Map.of("maxConcurrentMatches",capacity),400);
        assertThat(request(path,"GET",null,200).path("revision").asLong()).isEqualTo(revision);
        var created=request("/api/tournaments","POST",Map.of("name","Admin-created capacity","maxConcurrentMatches",1),201);assertThat(created.path("maxConcurrentMatches").asInt()).isEqualTo(1);
        request("/api/auth/logout","POST",Map.of(),200);account=owner;request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);
        var updated=request(path+"/rules","POST",Map.of("tieBreakOrder",List.of("POINTS","HEAD_TO_HEAD")),200);
        assertThat(updated.path("maxConcurrentMatches").asInt()).isEqualTo(2);assertThat(updated.path("tieBreakOrder").get(1).asText()).isEqualTo("HEAD_TO_HEAD");
        request(path+"/rules","POST",Map.of("maxConcurrentMatches",4),403);
        assertThat(request(path,"GET",null,200).path("maxConcurrentMatches").asInt()).isEqualTo(2);
    }
}
