package com.rpsbattle.account;

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
class AccountAuthorizationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    final String password="Correct Account Password";
    class Browser implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        JsonNode request(String path,String method,Object body,boolean csrf,int status) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(csrf) {var token=request("/api/auth/csrf","GET",null,false,200);builder.header(token.path("headerName").asText(),token.path("token").asText());}
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var result=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).as(result.body()).isEqualTo(status);return json.readTree(result.body());
        }
        User register() throws Exception {
            String name="account_"+UUID.randomUUID().toString().substring(0,8);
            request("/api/auth/register","POST",Map.of("username",name,"email",name+"@example.com","displayName","Player","password",password),true,201);
            return users.findByUsername(name).orElseThrow();
        }
        void login(User user) throws Exception {request("/api/auth/login","POST",Map.of("username",user.username(),"password",password),true,200);}
        public void close() {http.close();}
    }
    private User admin(User user) {
        return users.save(new User(user.id(),user.username(),user.email(),user.passwordHash(),user.displayName(),UserRole.ADMIN,
                user.status(),user.createdAt(),user.updatedAt(),user.lastLoginAt(),user.authVersion()));
    }
    @Test void profileUsesSessionIdentityAndOnlyDisplayNameCanBeChanged() throws Exception {
        try(var a=new Browser();var b=new Browser()) {
            var alice=a.register();var bob=b.register();a.login(alice);b.login(bob);
            var own=a.request("/api/account/profile?userId="+bob.id(),"GET",null,false,200);
            assertThat(own.path("id").asText()).isEqualTo(alice.id().toString());
            assertThat(own.path("email").asText()).isEqualTo(alice.email());
            assertThat(own.toString()).doesNotContain("passwordHash","authVersion",password,"token");
            var updated=a.request("/api/account/profile","PATCH",Map.of("displayName"," New name ","id",bob.id().toString(),
                    "username",bob.username(),"email",bob.email(),"role","ADMIN","passwordHash","spoof"),true,200);
            assertThat(updated.path("displayName").asText()).isEqualTo("New name");
            assertThat(updated.path("id").asText()).isEqualTo(alice.id().toString());assertThat(updated.path("role").asText()).isEqualTo("USER");
            assertThat(users.findById(alice.id()).orElseThrow().email()).isEqualTo(alice.email());
            assertThat(users.findById(bob.id()).orElseThrow().displayName()).isEqualTo("Player");
            a.request("/api/account/profile","PATCH",Map.of("displayName"," "),true,400);
            a.request("/api/account/profile","PATCH",Map.of("displayName","x".repeat(61)),true,400);
            a.request("/api/account/profile","PATCH",Map.of("displayName","No CSRF"),false,403);
        }
    }
    @Test void passwordChangeRequiresOldPasswordAndRevokesEveryExistingSession() throws Exception {
        try(var first=new Browser();var second=new Browser()) {
            var user=first.register();first.login(user);second.login(user);
            first.request("/api/account/change-password","POST",Map.of("currentPassword","Wrong Password 123","newPassword","Replacement Password 123"),true,400);
            first.request("/api/account/change-password","POST",Map.of("currentPassword",password,"newPassword","short"),true,400);
            first.request("/api/account/change-password","POST",Map.of("currentPassword",password,"newPassword",password),true,400);
            var changed=first.request("/api/account/change-password","POST",Map.of("currentPassword",password,"newPassword","Replacement Password 123"),true,200);
            assertThat(changed.path("authenticated").asBoolean()).isFalse();
            first.request("/api/account/profile","GET",null,false,401);
            second.request("/api/account/profile","GET",null,false,401);
            first.request("/api/auth/login","POST",Map.of("username",user.username(),"password",password),true,401);
            first.request("/api/auth/login","POST",Map.of("username",user.username(),"password","Replacement Password 123"),true,200);
            assertThat(users.findById(user.id()).orElseThrow().authVersion()).isEqualTo(1);
        }
    }
    @Test void adminDisableEnableNeverRestoresOldSessionsAndDoesNotDeleteUser() throws Exception {
        try(var owner=new Browser();var guest=new Browser();var untouchedSession=new Browser()) {
            var administrator=admin(owner.register());var target=guest.register();owner.login(administrator);guest.login(target);untouchedSession.login(target);
            guest.request("/api/admin/users","GET",null,false,403);
            guest.request("/api/admin/users/"+administrator.id()+"/disable","POST",Map.of("reason","Account moderation test"),true,403);
            owner.request("/api/admin/users/"+target.id()+"/disable","POST",Map.of("reason","Account moderation test"),false,403);
            var disabled=owner.request("/api/admin/users/"+target.id()+"/disable","POST",Map.of("reason","Account moderation test"),true,200);
            assertThat(disabled.path("status").asText()).isEqualTo("DISABLED");
            guest.request("/api/account/profile","GET",null,false,401);
            guest.request("/api/auth/login","POST",Map.of("username",target.username(),"password",password),true,401);
            owner.request("/api/admin/users/"+target.id()+"/enable","POST",Map.of("reason","Account moderation test"),true,200);
            untouchedSession.request("/api/account/profile","GET",null,false,401);
            guest.login(target);
            assertThat(users.findById(target.id())).isPresent();
            owner.request("/api/admin/users/"+administrator.id()+"/disable","POST",Map.of("reason","Account moderation test"),true,400);
            owner.request("/api/admin/users/"+UUID.randomUUID()+"/disable","POST",Map.of("reason","Account moderation test"),true,404);
            var page=owner.request("/api/admin/users?page=0&size=2","GET",null,false,200);
            assertThat(page.path("users").size()).isEqualTo(2);
            assertThat(page.toString()).doesNotContain("passwordHash","password_hash","authVersion","sourcePath");
            owner.request("/api/admin/users?size=101","GET",null,false,400);
            assertThat(owner.request("/api/admin/system","GET",null,false,200).has("activeMatches")).isTrue();
        }
    }
    @Test void serverRechecksRoleAndAnonymousAccessHasNoAccountData() throws Exception {
        try(var browser=new Browser();var guest=new Browser()) {
            guest.request("/api/account/profile","GET",null,false,401);guest.request("/api/admin/system","GET",null,false,401);
            var user=admin(browser.register());browser.login(user);browser.request("/api/admin/system","GET",null,false,200);
            users.save(new User(user.id(),user.username(),user.email(),user.passwordHash(),user.displayName(),UserRole.USER,
                    user.status(),user.createdAt(),user.updatedAt(),user.lastLoginAt(),user.authVersion()));
            browser.request("/api/admin/system","GET",null,false,403);
            assertThat(browser.request("/api/account/profile","GET",null,false,200).path("role").asText()).isEqualTo("USER");
        }
    }
}
