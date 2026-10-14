package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentRulesApiTest extends StageThreeWebFixture {
    @Test void createKnockoutWith32SlotsAndOwnerRulesLockAtReady() throws Exception {
        loginAccount();var owner=account;
        var cup=request("/api/tournaments","POST",Map.of("name","Knockout","format","SINGLE_ELIMINATION","maxParticipants",32,"maxConcurrentMatches",1),201);
        assertThat(cup.path("format").asText()).isEqualTo("SINGLE_ELIMINATION");assertThat(cup.path("maxParticipants").asInt()).isEqualTo(32);
        String path="/api/tournaments/"+cup.path("id").asText();
        var rules=Map.of("maxConcurrentMatches",2,"tieBreakOrder",List.of("POINTS","AVERAGE_MOVE_TIME","HEAD_TO_HEAD"));
        assertThat(request(path+"/rules","POST",rules,200).path("tieBreakOrder").get(1).asText()).isEqualTo("AVERAGE_MOVE_TIME");
        request(path+"/rules","POST",Map.of("maxConcurrentMatches",0),400);
        request(path+"/rules","POST",Map.of("maxConcurrentMatches",2,"tieBreakOrder",List.of("WINS","POINTS")),400);
        request(path+"/rules","POST",Map.of("maxConcurrentMatches",2,"tieBreakOrder",List.of("POINTS","WINS","WINS")),400);
        request("/api/tournaments","POST",Map.of("name","Invalid","maxParticipants",33),400);
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(path+"/rules","POST",rules,403);
        account=owner;request("/api/auth/login","POST",Map.of("username",owner.username(),"password","Tournament Test Password"),200);
        request(path+"/participants","POST",Map.of("botId",upload("Rules A").id()),200);request(path+"/participants","POST",Map.of("botId",upload("Rules B").id()),200);
        request(path+"/open-registration","POST",Map.of(),200);request(path+"/close-registration","POST",Map.of(),200);
        request(path+"/rules","POST",rules,400);assertThat(request(path,"GET",null,200).path("maxConcurrentMatches").asInt()).isEqualTo(2);
    }
}
