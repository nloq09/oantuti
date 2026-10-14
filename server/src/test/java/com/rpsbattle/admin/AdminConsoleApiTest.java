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
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
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
            assertThat(dashboard.path("openDisputes").isNull()).isTrue();assertThat(dashboard.path("dockerContainers").isNull()).isTrue();
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
}
