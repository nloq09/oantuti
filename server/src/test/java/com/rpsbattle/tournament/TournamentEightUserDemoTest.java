package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.rpsbattle.account.domain.User;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.bots.enabled=true","rps.tournament.bot-delay-millis=0","rps.bots.directory=${java.io.tmpdir}/rps-eight-user-${random.uuid}"})
@Testcontainers(disabledWithoutDocker=true)
class TournamentEightUserDemoTest extends StageThreeWebFixture {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");}
    void signIn(User user)throws Exception {account=user;request("/api/auth/login","POST",Map.of("username",user.username(),"password","Tournament Test Password"),200);}
    @Test void eightUsersRegisterApproveCheckInWatchAndReplayCompleteKnockout()throws Exception {
        assumeTrue(uploads.available(com.rpsbattle.bot.upload.BotLanguage.PYTHON));loginAccount();var owner=account;
        var cup=participantTournament(Map.of("name","Eight user demo","format","SINGLE_ELIMINATION","maxParticipants",8,"checkInRequired",true));String id=cup.path("id").asText(),path="/api/tournaments/"+id;
        managerRequest(path+"/open-registration","POST",Map.of(),200);var players=new ArrayList<User>();var registrations=new ArrayList<String>();
        for(int i=0;i<8;i++) {
            if(i>0){request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();}
            players.add(account);var bot=upload("Demo user "+i);var registered=request(path+"/registrations","POST",Map.of("botId",bot.id()),200);
            registrations.add(registered.path("registrations").findValues("id").stream().map(com.fasterxml.jackson.databind.JsonNode::asText).filter(key->!registrations.contains(key)).findFirst().orElseThrow());
        }
        signIn(owner);for(var registration:registrations)managerRequest(path+"/registrations/"+registration+"/approve","POST",Map.of(),200);
        managerRequest(path+"/close-registration","POST",Map.of(),200);
        for(int i=0;i<8;i++){signIn(players.get(i));request(path+"/check-in","POST",Map.of("registrationId",registrations.get(i)),200);}
        signIn(owner);var ready=managerRequest(path+"/close-check-in","POST",Map.of(),200);assertThat(ready.path("participants").size()).isEqualTo(8);
        assertThat(ready.path("participants").findValuesAsText("artifactHash")).allMatch(hash->hash.matches("[a-f0-9]{64}"));assertThat(ready.path("rulesSnapshot").path("boardRows").asInt()).isEqualTo(9);
        try(var watcher=new Peer()) {
            watcher.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",id));watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE"));managerRequest(path+"/start","POST",Map.of(),200);
            var running=watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE")&&m.path("payload").path("tournament").path("progress").path("running").asInt()>0);
            var game=running.path("payload").path("tournament").path("matches").findValues("onlineMatchId").stream().filter(n->!n.isNull()).findFirst().orElseThrow().asText();
            try(var spectator=new Peer()){spectator.send(Map.of("type","SPECTATE_MATCH","matchId",game));spectator.await(m->m.path("type").asText().equals("GAME_STATE"));spectator.send(Map.of("type","ACTION","matchId",game));assertThat(spectator.await(m->m.path("type").asText().equals("ACTION_REJECTED")).path("payload").path("error").asText()).isEqualTo("SPECTATOR_READ_ONLY");}
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);com.fasterxml.jackson.databind.JsonNode finished=null;
            while(System.nanoTime()<deadline){finished=request(path,"GET",null,200);if(finished.path("status").asText().equals("FINISHED"))break;Thread.sleep(150);}
            assertThat(finished.path("status").asText()).isEqualTo("FINISHED");assertThat(finished.path("matches").size()).isEqualTo(7);assertThat(finished.path("progress").path("failed").asInt()).isZero();assertThat(finished.path("champion").isNull()).isFalse();
            for(var match:finished.path("matches"))request("/api/matches/"+match.path("historyMatchId").asText()+"/replay","GET",null,200);
            assertThat(managerRequest(path+"/export","GET",null,200).path("audit").size()).isGreaterThan(20);assertThat(send(organizer(path).http(),"/api/account/tournaments?filter=created","GET",null,200,Map.of()).toString()).contains(id);
        }
    }
}
