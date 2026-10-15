package com.rpsbattle.admin;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminConsoleApiTest {
    @Test void systemAndReadOnlyAuditAreAdminOnlyAndRejectedPayloadsAreNeverLogged()throws Exception{
        try(var admin=new Browser();var ordinary=new Browser();var guest=new Browser()){
            admin.account(true);ordinary.account(false);guest.call("/api/admin/audit",null,false,401);ordinary.call("/api/admin/system",null,false,403);
            admin.call("/api/admin/users/"+UUID.randomUUID()+"/disable",Map.of("reason","private-payload-must-not-be-recorded"),false,403);
            var events=admin.call("/api/admin/audit?result=FAILURE",null,false,200);assertThat(events.path("total").asLong()).isPositive();assertThat(events.toString()).doesNotContain("private-payload-must-not-be-recorded","password","token","sessionId");
            admin.call("/api/admin/audit?result=UNKNOWN",null,false,400);
            var system=admin.call("/api/admin/system",null,false,200);assertThat(system.path("application").asText()).isEqualTo("UP");assertThat(system.path("postgresql").asText()).isEqualTo("UP");
            assertThat(system.path("docker").path("status").asText()).isIn("UP","DOWN");assertThat(system.path("workers").path("maxConcurrentMatches").asInt()).isBetween(1,4);assertThat(system.has("alerts")).isTrue();
            assertThat(system.toString()).doesNotContain("password","source_path","docker.sock","JSESSIONID");
        }
    }
    @Autowired com.rpsbattle.tournament.TournamentService tournaments;
    @Autowired com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator tournamentPersistence;
    @Test void disputeLifecycleUsesExistingReviewFactsAndKeepsClosedRequestsReadOnly()throws Exception{
        try(var admin=new Browser();var ordinary=new Browser();var guest=new Browser()){
            var user=admin.account(true);ordinary.account(false);
            var created=admin.call("/api/tournaments",Map.of("name","Dispute fixture","format","ROUND_ROBIN"),true,201);String id=created.path("id").asText();var t=tournaments.find(id);var now=java.time.Instant.now();
            for(int i=0;i<2;i++){var botId=UUID.randomUUID();new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->bots.create(new com.rpsbattle.bot.ownership.BotMetadata(botId,user.id().value(),"Review bot",com.rpsbattle.bot.upload.BotLanguage.PYTHON,"fixture/source",com.rpsbattle.bot.ownership.BotMetadata.Status.READY,now,now)));t.participants.add(new com.rpsbattle.tournament.domain.TournamentParticipant(UUID.randomUUID().toString(),botId.toString(),"never-public","Review bot "+i,i,user.id().value()));}
            var m=new com.rpsbattle.tournament.domain.TournamentMatch(UUID.randomUUID().toString(),id,t.participants.get(0),t.participants.get(1));m.status=com.rpsbattle.tournament.domain.TournamentMatch.Status.FINISHED;m.winnerParticipantId=m.participantA.participantId();m.result="WIN";m.startedAt=now;m.finishedAt=now;t.matches.add(m);t.status=com.rpsbattle.tournament.domain.TournamentStatus.PAUSED;t.startedAt=now;t.revision++;tournamentPersistence.persistNow(t);
            admin.call("/api/tournaments/"+id+"/matches/"+m.id+"/review",Map.of("revision",t.revision,"reason","Verify original result"),true,200);
            guest.call("/api/admin/disputes",null,false,401);ordinary.call("/api/admin/disputes",null,false,403);ordinary.call("/api/admin/replays",null,false,403);
            var list=admin.call("/api/admin/disputes?tournament="+id+"&status=OPEN",null,false,200);assertThat(list.path("total").asInt()).isEqualTo(1);String dispute=list.path("items").get(0).path("id").asText();
            var detail=admin.call("/api/admin/disputes/"+dispute,null,false,200);assertThat(detail.toString()).doesNotContain("never-public","source_path");
            admin.call("/api/admin/disputes/"+dispute+"/assign",Map.of("revision",detail.path("revision").asLong(),"reason","Investigation"),false,403);
            detail=admin.call("/api/admin/disputes/"+dispute+"/assign",Map.of("revision",detail.path("revision").asLong(),"reason","Investigation"),true,200);assertThat(detail.path("status").asText()).isEqualTo("UNDER_REVIEW");
            detail=admin.call("/api/admin/disputes/"+dispute+"/reject",Map.of("revision",detail.path("revision").asLong(),"reason","Original result confirmed"),true,200);assertThat(detail.path("status").asText()).isEqualTo("REJECTED");assertThat(m.winnerParticipantId).isEqualTo(m.participantA.participantId());
            admin.call("/api/admin/disputes/"+dispute+"/uphold",Map.of("revision",detail.path("revision").asLong(),"reason","Duplicate decision"),true,409);
            admin.call("/api/admin/replays",null,false,200);admin.call("/api/admin/replays?state=UNKNOWN",null,false,400);
        }
    }
    @Test void tournamentAdministrationUsesRevisionReasonAndRealAdminAuthorization()throws Exception{
        try(var admin=new Browser();var owner=new Browser();var guest=new Browser()){
            admin.account(true);owner.account(false);var cup=owner.call("/api/tournaments",Map.of("name","Admin lifecycle fixture","format","ROUND_ROBIN"),true,201);String id=cup.path("id").asText();
            guest.call("/api/admin/tournaments",null,false,401);owner.call("/api/admin/tournaments/"+id,null,false,403);
            var list=admin.call("/api/admin/tournaments?q=Admin%20lifecycle&status=DRAFT",null,false,200);assertThat(list.path("total").asLong()).isPositive();
            String action="/api/admin/tournaments/"+id+"/actions/";long revision=cup.path("revision").asLong();
            admin.call(action+"open-registration",Map.of("revision",revision,"reason","Open for test"),false,403);
            admin.call(action+"open-registration",Map.of("revision",revision),true,400);
            cup=admin.call(action+"open-registration",Map.of("revision",revision,"reason","Open for test"),true,200);
            assertThat(cup.path("status").asText()).isEqualTo("REGISTRATION");assertThat(cup.path("audit").get(cup.path("audit").size()-1).path("reason").asText()).isEqualTo("Open for test");
            admin.call(action+"cancel",Map.of("revision",revision,"reason","Stale request"),true,409);
            cup=admin.call(action+"cancel",Map.of("revision",cup.path("revision").asLong(),"reason","Cancel fixture"),true,200);assertThat(cup.path("status").asText()).isEqualTo("CANCELLED");
        }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired com.rpsbattle.bot.ownership.BotRepository bots;
    @Autowired com.rpsbattle.admin.BotAdministrationPolicy botPolicy;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.rpsbattle.history.MatchPersistenceCoordinator history;
    final String password="Admin Console Test Password";
    class Browser implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        JsonNode call(String path,Object body,boolean csrf,int status)throws Exception{
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(csrf){var token=call("/api/auth/csrf",null,false,200);builder.header(token.path("headerName").asText(),token.path("token").asText());}
            builder.method(body==null?"GET":"POST",body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return json.readTree(response.body());
        }
        User account(boolean admin)throws Exception{
            String name="console_"+UUID.randomUUID().toString().substring(0,8);
            call("/api/auth/register",Map.of("username",name,"email",name+"@example.com","displayName","Console Test","password",password),true,201);
            var user=users.findByUsername(name).orElseThrow();
            if(admin)user=users.save(new User(user.id(),user.username(),user.email(),user.passwordHash(),user.displayName(),UserRole.ADMIN,user.status(),user.createdAt(),user.updatedAt(),user.lastLoginAt(),user.authVersion()));
            call("/api/auth/login",Map.of("username",name,"password",password),true,200);return user;
        }
        public void close(){http.close();}
    }
    @Test void dashboardRequiresRealAdminAndDoesNotInventUnsupportedMeasurements()throws Exception{
        try(var guest=new Browser();var ordinary=new Browser();var admin=new Browser()){
            guest.call("/api/admin/dashboard",null,false,401);ordinary.account(false);ordinary.call("/api/admin/dashboard",null,false,403);admin.account(true);
            var dashboard=admin.call("/api/admin/dashboard",null,false,200);
            assertThat(dashboard.path("totalUsers").asLong()).isPositive();assertThat(dashboard.path("workers").path("maxConcurrentMatches").asInt()).isBetween(1,4);
            assertThat(dashboard.path("openDisputes").asLong()).isGreaterThanOrEqualTo(0);assertThat(dashboard.has("dockerContainers")).isTrue();
            assertThat(dashboard.toString()).doesNotContain("password","token","source_path","sessionId");
        }
    }
    @Test void userFiltersReasonAuditAndRevocationAreEnforced()throws Exception{
        try(var admin=new Browser();var target=new Browser();var guest=new Browser()){
            var actor=admin.account(true);var user=target.account(false);
            guest.call("/api/admin/users/"+user.id(),null,false,401);target.call("/api/admin/users/"+actor.id(),null,false,403);
            var page=admin.call("/api/admin/users?q="+user.username()+"&role=USER&status=ACTIVE",null,false,200);
            assertThat(page.path("total").asInt()).isEqualTo(1);assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(user.id().toString());
            admin.call("/api/admin/users?role=ROOT",null,false,400);admin.call("/api/admin/users?from=bad",null,false,400);
            admin.call("/api/admin/users/"+user.id()+"/disable",Map.of("reason","Test moderation"),false,403);
            admin.call("/api/admin/users/"+user.id()+"/disable",Map.of("reason"," "),true,400);
            admin.call("/api/admin/users/"+user.id()+"/disable",Map.of("reason","Test moderation"),true,200);
            target.call("/api/account/profile",null,false,401);
            var detail=admin.call("/api/admin/users/"+user.id(),null,false,200);
            assertThat(detail.path("status").asText()).isEqualTo("DISABLED");assertThat(detail.path("audit").get(0).path("reason").asText()).isEqualTo("Test moderation");
            assertThat(detail.toString()).doesNotContain("password_hash","source_path","auth_version");
            admin.call("/api/admin/users/"+user.id()+"/enable",Map.of("reason","Review complete"),true,200);
            target.call("/api/account/profile",null,false,401);
            admin.call("/api/admin/users/"+actor.id()+"/disable",Map.of("reason","Self disable"),true,400);
        }
    }
    @Test void botAdministrationIsAuditedAndBlocksNewExecutionWithoutRevealingStoragePaths()throws Exception{
        try(var admin=new Browser();var owner=new Browser();var guest=new Browser()){
            admin.account(true);var user=owner.account(false);var id=UUID.randomUUID();var now=java.time.Instant.now();
            new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->bots.create(new com.rpsbattle.bot.ownership.BotMetadata(id,user.id().value(),"Console bot",com.rpsbattle.bot.upload.BotLanguage.PYTHON,"secret/source/path",com.rpsbattle.bot.ownership.BotMetadata.Status.UPLOADED,now,now)));
            guest.call("/api/admin/bots",null,false,401);owner.call("/api/admin/bots/"+id,null,false,403);
            var page=admin.call("/api/admin/bots?owner="+user.id()+"&language=PYTHON",null,false,200);assertThat(page.path("total").asInt()).isEqualTo(1);
            admin.call("/api/admin/bots/"+id+"/disable",Map.of("reason","Quarantine"),false,403);
            admin.call("/api/admin/bots/"+id+"/disable",Map.of("reason"," "),true,400);
            var disabled=admin.call("/api/admin/bots/"+id+"/disable",Map.of("reason","Quarantine"),true,200);
            assertThat(disabled.path("admin_disabled").asBoolean()).isTrue();assertThat(disabled.path("audit").get(0).path("reason").asText()).isEqualTo("Quarantine");
            assertThat(disabled.toString()).doesNotContain("secret/source/path","source_path","token","password");
            assertThatThrownBy(()->botPolicy.requireEnabled(id.toString())).isInstanceOf(com.rpsbattle.auth.AuthException.class).hasMessage("BOT_DISABLED");
            owner.call("/api/matches",Map.of("mode","HUMAN_VS_BOT","botId",id.toString()),true,409);
            admin.call("/api/admin/bots/"+id+"/enable",Map.of("reason","Reviewed"),true,200);botPolicy.requireEnabled(id.toString());
            admin.call("/api/admin/bots/"+id+"/archive",Map.of("reason","Retired"),true,200);
            assertThat(bots.find(id).orElseThrow().status()).isEqualTo(com.rpsbattle.bot.ownership.BotMetadata.Status.DELETED);
            admin.call("/api/admin/bots/"+id+"/enable",Map.of("reason","Cannot restore"),true,409);
        }
    }
    @Test void matchMetadataIncludesLiveReadOnlyLinksAndRejectsOrdinaryUsers()throws Exception{
        try(var admin=new Browser();var owner=new Browser();var guest=new Browser()){
            admin.account(true);var user=owner.account(false);
            var created=owner.call("/api/matches",Map.of("mode","HUMAN_VS_HUMAN","seed",7),true,201);
            history.synchronize();
            String id=created.path("historyId").asText();
            guest.call("/api/admin/matches",null,false,401);owner.call("/api/admin/matches/"+id,null,false,403);
            var list=admin.call("/api/admin/matches?user="+user.id(),null,false,200);assertThat(list.path("total").asInt()).isPositive();
            var detail=admin.call("/api/admin/matches/"+id,null,false,200);
            assertThat(detail.path("id").asText()).isEqualTo(id);assertThat(detail.path("live_available").asBoolean()).isTrue();
            assertThat(detail.path("room_id").asText()).isEqualTo(created.path("matchId").asText());
            assertThat(detail.toString()).doesNotContain(created.path("token").asText(),"password","source_path");
            admin.call("/api/admin/matches?status=UNKNOWN",null,false,400);admin.call("/api/admin/matches?bot=bad",null,false,400);
            admin.call("/api/admin/matches/unknown",null,false,404);
        }
    }
}
