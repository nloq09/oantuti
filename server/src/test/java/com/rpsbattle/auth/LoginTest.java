package com.rpsbattle.auth;

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
class LoginTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    private final String password="Correct Password 123";

    class Browser implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> call(String path,String method,Object body,boolean csrf,int status) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(csrf) {
                var token=json.readTree(call("/api/auth/csrf","GET",null,false,200).body());
                builder.header(token.path("headerName").asText(),token.path("token").asText());
            }
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return response;
        }
        JsonNode getMe() throws Exception { return json.readTree(call("/api/auth/me","GET",null,false,200).body()); }
        String sessionId() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
        JsonNode register(String username) throws Exception {
            return json.readTree(call("/api/auth/register","POST",Map.of("username",username,"email",username+"@example.com","password",password,"displayName","Player","role","ADMIN"),true,201).body());
        }
        public void close() {http.close();}
    }
    String name() { return "login_"+UUID.randomUUID().toString().substring(0,8); }

    @Test void registerLoginCookieRotationMeAndLogoutInvalidateOldSession() throws Exception {
        try(var browser=new Browser()) {
            assertThat(browser.getMe().path("authenticated").asBoolean()).isFalse();
            var csrfResponse=browser.call("/api/auth/csrf","GET",null,false,200);
            assertThat(csrfResponse.headers().firstValue("Set-Cookie").orElseThrow()).contains("HttpOnly","SameSite=Lax");
            String username=name();var created=browser.register(username.toUpperCase());
            assertThat(created.path("role").asText()).isEqualTo("USER");
            assertThat(created.toString()).doesNotContain(password,"password","email","session","token");
            assertThat(browser.getMe().path("authenticated").asBoolean()).isFalse();
            String oldSession=browser.sessionId();
            var loggedIn=browser.call("/api/auth/login","POST",Map.of("username",username.toUpperCase(),"password",password),true,200);
            String authenticatedSession=browser.sessionId();
            assertThat(authenticatedSession).isNotEqualTo(oldSession);
            assertThat(loggedIn.headers().firstValue("Set-Cookie").orElseThrow()).contains("HttpOnly","SameSite=Lax");
            assertThat(browser.getMe().path("user").path("id").asText()).isEqualTo(created.path("id").asText());
            assertThat(users.findByUsername(username).orElseThrow().lastLoginAt()).isNotNull();
            assertThat(users.findByUsername(username).orElseThrow().passwordHash()).isNotEqualTo(password);
            browser.call("/api/admin/users","GET",null,false,403);
            assertAnonymousCookie(oldSession);
            var logout=browser.call("/api/auth/logout","POST",Map.of(),true,200);
            assertThat(logout.headers().allValues("Set-Cookie")).anySatisfy(value->assertThat(value).contains("Max-Age=0","HttpOnly"));
            assertThat(browser.getMe().path("authenticated").asBoolean()).isFalse();
            assertAnonymousCookie(authenticatedSession);
        }
    }
    private void assertAnonymousCookie(String cookie) throws Exception {
        try(var http=HttpClient.newHttpClient()) {
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/me"))
                    .header("Cookie","JSESSIONID="+cookie).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(json.readTree(response.body()).path("authenticated").asBoolean()).isFalse();
        }
    }
    @Test void requiresCsrfOnRegisterLoginLogoutAndRejectsInvalidCredentialsGenerically() throws Exception {
        try(var browser=new Browser()) {
            browser.call("/api/auth/register","POST",Map.of(),false,403);
            browser.call("/api/auth/login","POST",Map.of(),false,403);
            browser.call("/api/auth/logout","POST",Map.of(),false,403);
            String username=name();browser.register(username);
            for(String loginName:List.of(username,"unknown_account")) {
                var failed=browser.call("/api/auth/login","POST",Map.of("username",loginName,"password","Wrong Password 123"),true,401);
                assertThat(json.readTree(failed.body()).path("error").asText()).isEqualTo("INVALID_CREDENTIALS");
            }
            assertThat(browser.getMe().path("authenticated").asBoolean()).isFalse();
            browser.call("/api/auth/register","POST",Map.of("username",username.toUpperCase(),"email","different@example.com","password",password,"displayName","Duplicate"),true,409);
            browser.call("/api/account/profile","GET",null,false,401);
            browser.call("/api/health","GET",null,false,200);
        }
    }
    @Test void disabledAccountsCannotLoginAndExistingSessionBecomesAnonymous() throws Exception {
        try(var browser=new Browser()) {
            String username=name();browser.register(username);
            browser.call("/api/auth/login","POST",Map.of("username",username,"password",password),true,200);
            var user=users.findByUsername(username).orElseThrow();
            users.save(new User(user.id(),user.username(),user.email(),user.passwordHash(),user.displayName(),user.role(),AccountStatus.DISABLED,user.createdAt(),user.updatedAt(),user.lastLoginAt()));
            assertThat(browser.getMe().path("authenticated").asBoolean()).isFalse();
            browser.call("/api/auth/login","POST",Map.of("username",username,"password",password),true,401);
        }
    }
}
