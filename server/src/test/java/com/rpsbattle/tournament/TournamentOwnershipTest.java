package com.rpsbattle.tournament;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.BotUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rps.bots.enabled=false")
class TournamentOwnershipTest {
    @TempDir static Path root;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {registry.add("rps.bots.directory",root::toString);}
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired OwnedBotService bots;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean BotUploadService artifacts;
    @Autowired TournamentService tournaments;
    class Browser implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        User user;
        JsonNode request(String path,String method,Object body,int status) throws Exception {return request(path,method,body,status,Map.of(),true);}
        JsonNode request(String path,String method,Object body,int status,Map<String,String> headers,boolean csrf) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");headers.forEach(builder::header);
            if(csrf && !method.equals("GET")) {var token=request("/api/auth/csrf","GET",null,200,Map.of(),false);builder.header(token.path("headerName").asText(),token.path("token").asText());}
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return json.readTree(response.body());
        }
        void login() throws Exception {var name="tour_"+UUID.randomUUID().toString().substring(0,8);request("/api/auth/register","POST",Map.of("username",name,"email",name+"@example.com","displayName","Player","password","Tournament Account Password"),201);request("/api/auth/login","POST",Map.of("username",name,"password","Tournament Account Password"),200);user=users.findByUsername(name).orElseThrow();}
        String upload() {return bots.upload(AuthService.sessionAuthentication(user),"Bot "+user.username(),new MockMultipartFile("file","main.py","text/plain","print(1)".getBytes())).id().toString();}
        public void close() {http.close();}
    }
    String participant(JsonNode tournament,String botId) {for(var p:tournament.path("participants")) if(p.path("botId").asText().equals(botId)) return p.path("participantId").asText();throw new AssertionError("missing participant");}
    @Test void accountOwnsTournamentAndLegacyTokensOrClaimedRolesCannotManageIt() throws Exception {
        try(var a=new Browser();var b=new Browser();var guest=new Browser()) {
            guest.request("/api/tournaments","POST",Map.of("name","Guest"),403,Map.of(),false);guest.request("/api/tournaments","POST",Map.of("name","Guest"),401);a.login();b.login();
            var created=a.request("/api/tournaments","POST",Map.of("name","Owned","ownerUserId",b.user.id().toString(),"role","ADMIN"),201);String path="/api/tournaments/"+created.path("id").asText();
            assertThat(created.path("ownerUserId").asText()).isEqualTo(a.user.id().toString());assertThat(created.has("ownerToken")).isFalse();
            var legacy=tournaments.find(created.path("id").asText()).ownerToken;
            b.request(path+"/start","POST",Map.of("role","ADMIN"),403,Map.of("X-Tournament-Token",legacy),true);b.request(path+"/cancel","POST",Map.of(),403,Map.of("X-Tournament-Token",legacy),true);
            a.request(path+"/start","POST",Map.of(),400);guest.request(path+"/cancel","POST",Map.of(),401);
            assertThat(guest.request(path,"GET",null,200).toString()).doesNotContain(legacy,"ownerToken","password");guest.request(path+"/ranking","GET",null,200);guest.request(path+"/export","GET",null,200);
            a.request("/api/tournaments","POST",Map.of("name","Private","visibility","PRIVATE"),400);a.request(path+"/cancel","POST",Map.of(),403,Map.of(),false);
            b.user=users.save(new User(b.user.id(),b.user.username(),b.user.email(),b.user.passwordHash(),b.user.displayName(),UserRole.ADMIN,b.user.status(),b.user.createdAt(),b.user.updatedAt(),b.user.lastLoginAt(),b.user.authVersion()));
            b.request(path+"/cancel","POST",Map.of(),200);
        }
    }
    @Test void otherAccountsCanJoinWithOwnBotsWithdrawAndOwnerCanEditDraftRoster() throws Exception {
        // This test isolates authorization; real sandbox registration is covered by Docker tests.
        org.mockito.Mockito.doNothing().when(artifacts).validateRegistration(org.mockito.ArgumentMatchers.any());
        try(var a=new Browser();var b=new Browser();var stranger=new Browser()) {
            a.login();b.login();stranger.login();var botA=a.upload();var botB=b.upload();var cup=a.request("/api/tournaments","POST",Map.of("name","Open Cup"),201);var path="/api/tournaments/"+cup.path("id").asText();
            a.request(path+"/participants","POST",Map.of("botId",botB),403);
            a.request(path+"/participants","POST",Map.of("botId",botA),200);
            b.request(path+"/participants","POST",Map.of("botId",botB,"ownerUserId",a.user.id().toString()),403);
            assertThat(a.request(path,"GET",null,200).path("participants").size()).isEqualTo(1);
            a.request(path+"/open-registration","POST",Map.of(),200);
            var pending=b.request(path+"/participants","POST",Map.of("botId",botB),200);
            assertThat(pending.path("participants").size()).isEqualTo(1);
            String registration="";for(var r:pending.path("registrations"))if(r.path("botId").asText().equals(botB)){registration=r.path("id").asText();assertThat(r.path("status").asText()).isEqualTo("PENDING");}
            b.request(path+"/registrations/"+registration+"/approve","POST",Map.of(),403);
            var joined=a.request(path+"/registrations/"+registration+"/approve","POST",Map.of(),200);
            assertThat(joined.path("participants").size()).isEqualTo(2);
            for(var p:joined.path("participants")) if(p.path("botId").asText().equals(botB)) assertThat(p.path("ownerUserId").asText()).isEqualTo(b.user.id().toString());
            var pa=participant(joined,botA);var pb=participant(joined,botB);
            var stolen=artifacts.stored(botB).token();stranger.request(path+"/participants/"+pb,"DELETE",null,403,Map.of("X-Bot-Token",stolen),true);b.request(path+"/participants/"+pa,"DELETE",null,403);
            b.request(path+"/participants/"+pb,"DELETE",null,200);b.request(path+"/participants","POST",Map.of("botId",botB),200);
            var again=a.request(path+"/registrations/"+registration+"/approve","POST",Map.of(),200);
            a.request(path+"/participants/"+participant(again,botB),"DELETE",null,200);
            assertThat(a.request(path+"/export","GET",null,200).toString()).doesNotContain(stolen,"botToken","sourcePath");
        }
    }
}
