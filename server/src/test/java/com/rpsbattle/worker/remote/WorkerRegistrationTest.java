package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthException;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkerRegistrationTest {
    @LocalServerPort int port;
    @Autowired WorkerIdentityService identities;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    record Reply(int status,JsonNode body,HttpHeaders headers){}
    class Browser implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        Reply call(String path,String method,Object body,String bearer,boolean withCsrf)throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(withCsrf){var token=call("/api/auth/csrf","GET",null,null,false).body();builder.header(token.path("headerName").asText(),token.path("token").asText());}
            if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
            var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(),response.body().isBlank()?json.createObjectNode():json.readTree(response.body()),response.headers());
        }
        UUID account(boolean admin)throws Exception {
            String name="worker_"+UUID.randomUUID().toString().substring(0,8),password="Worker Foundation Password";
            assertThat(call("/api/auth/register","POST",Map.of("username",name,"email",name+"@example.com","displayName","Worker Test","password",password),null,true).status()).isEqualTo(201);
            var u=users.findByUsername(name).orElseThrow();
            if(admin)users.save(new User(u.id(),u.username(),u.email(),u.passwordHash(),u.displayName(),UserRole.ADMIN,u.status(),u.createdAt(),u.updatedAt(),u.lastLoginAt(),u.authVersion()));
            assertThat(call("/api/auth/login","POST",Map.of("username",name,"password",password),null,true).status()).isEqualTo(200);return u.id().value();
        }
        WorkerIdentityService.Token token(UUID actor,int uses){return identities.issue(actor,uses,15);}
        public void close(){http.close();}
    }
    WorkerIdentityService.Registration registration(String token){return new WorkerIdentityService.Registration(token,"LAB-PC-01","0.1.0",1);}
    @Test void adminTokensRequireAccountRoleAndCsrfButInternalIdentityIgnoresAccountCookies()throws Exception {
        try(var admin=new Browser();var guest=new Browser();var user=new Browser()) {
            admin.account(true);user.account(false);
            String endpoint="/api/admin/workers/registration-tokens";
            assertThat(guest.call(endpoint,"GET",null,null,false).status()).isEqualTo(401);
            assertThat(user.call(endpoint,"GET",null,null,false).status()).isEqualTo(403);
            assertThat(admin.call(endpoint,"POST",Map.of(),null,false).status()).isEqualTo(403);
            var issued=admin.call(endpoint,"POST",Map.of(),null,true);assertThat(issued.status()).isEqualTo(201);
            assertThat(issued.headers().firstValue("Cache-Control")).hasValue("no-store");
            String token=issued.body().path("registrationToken").asText();
            var enrolled=guest.call("/internal/workers/register","POST",registration(token),null,false);
            assertThat(enrolled.status()).isEqualTo(201);assertThat(enrolled.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(enrolled.body().path("worker").path("approvalStatus").asText()).isEqualTo("PENDING_APPROVAL");
            assertThat(enrolled.body().path("worker").path("trustLevel").asText()).isEqualTo("UNTRUSTED");
            String path="/internal/workers/"+enrolled.body().path("worker").path("workerId").asText()+"/identity";
            assertThat(admin.call(path,"GET",null,null,false).status()).isEqualTo(401);
            String credential=enrolled.body().path("workerCredential").asText();
            var result=guest.call(path,"GET",null,credential,false);assertThat(result.status()).isEqualTo(200);
            assertThat(result.body().toString()).doesNotContain(credential,token,"credentialHash");
            assertThat(guest.call(endpoint,"GET",null,credential,false).status()).isEqualTo(401);
            assertThat(guest.call("/internal/workers/"+UUID.randomUUID()+"/identity","GET",null,credential,false).status()).isEqualTo(403);
            assertThat(guest.call("/internal/workers/"+enrolled.body().path("worker").path("workerId").asText()+"/jobs/claim","POST",Map.of(),credential,false).status()).isEqualTo(403);
            var listed=admin.call(endpoint,"GET",null,null,false);assertThat(listed.body().toString()).doesNotContain(token,credential,"token_hash","credential_hash");
            assertThat(db.queryForObject("select token_hash from worker_registration_tokens where id=?",String.class,UUID.fromString(issued.body().path("id").asText()))).isEqualTo(WorkerIdentityService.hash(token));
        }
    }
    @Test void oneTimeTokenIsConsumedExactlyOnceAcrossConcurrentTransactions()throws Exception {
        try(var browser=new Browser();var pool=Executors.newFixedThreadPool(8)) {
            UUID actor=browser.account(true);var token=browser.token(actor,1);
            var start=new CountDownLatch(1);var attempts=new ArrayList<Future<Boolean>>();
            for(int i=0;i<8;i++)attempts.add(pool.submit(()->{start.await();try{identities.register(registration(token.registrationToken()));return true;}catch(AuthException error){assertThat(error.code()).isEqualTo("WORKER_REGISTRATION_TOKEN_INVALID");return false;}}));
            start.countDown();int successes=0;for(var attempt:attempts)if(attempt.get(20,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(db.queryForObject("select remaining_uses from worker_registration_tokens where id=?",Integer.class,token.id())).isZero();
        }
    }
    @Test void limitedExpiredAndRevokedTokensCannotOverEnroll()throws Exception {
        try(var browser=new Browser()) {
            UUID actor=browser.account(true);var token=browser.token(actor,2);
            var a=identities.register(registration(token.registrationToken()));var b=identities.register(registration(token.registrationToken()));
            assertThat(a.worker().workerId()).isNotEqualTo(b.worker().workerId());
            assertThatThrownBy(()->identities.register(registration(token.registrationToken()))).isInstanceOf(AuthException.class);
            var expired=browser.token(actor,1);db.update("update worker_registration_tokens set expires_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),expired.id());
            assertThatThrownBy(()->identities.register(registration(expired.registrationToken()))).isInstanceOf(AuthException.class);
            var revoked=browser.token(actor,1);
            assertThat(browser.call("/api/admin/workers/registration-tokens/"+revoked.id()+"/revoke","POST",Map.of(),null,true).status()).isEqualTo(200);
            identities.revokeToken(revoked.id(),actor);
            assertThatThrownBy(()->identities.register(registration(revoked.registrationToken()))).isInstanceOf(AuthException.class);
            assertThat(db.queryForObject("select count(*) from worker_identity_events where resource_id=? and event_type='REGISTRATION_TOKEN_REVOKED'",Integer.class,revoked.id())).isEqualTo(1);
        }
    }
    @Test void invalidRegistrationDoesNotConsumeTokenAndCredentialCannotActAsRegistrationToken()throws Exception {
        try(var browser=new Browser()) {
            var token=browser.token(browser.account(true),1);
            assertThat(browser.call("/internal/workers/register","POST",new WorkerIdentityService.Registration(token.registrationToken(),"PC","0.1.0",2),null,false).status()).isEqualTo(409);
            assertThat(browser.call("/internal/workers/register","POST",new WorkerIdentityService.Registration(token.registrationToken(),"<script>","0.1.0",1),null,false).status()).isEqualTo(400);
            assertThat(identities.tokens()).filteredOn(t->t.id().equals(token.id())).first().extracting(WorkerIdentityService.TokenView::remainingUses).isEqualTo(1);
            var enrolled=identities.register(registration(token.registrationToken()));
            assertThatThrownBy(()->identities.register(registration(enrolled.workerCredential()))).isInstanceOf(AuthException.class);
            assertThatThrownBy(()->identities.authenticate(token.registrationToken())).isInstanceOf(AuthException.class);
            assertThat(db.queryForObject("select credential_hash from worker_credentials where worker_id=?",String.class,enrolled.worker().workerId())).isEqualTo(WorkerIdentityService.hash(enrolled.workerCredential()));
            assertThatThrownBy(()->identities.issue(UUID.randomUUID(),0,1)).isInstanceOf(AuthException.class);
        }
    }
    @Test void persistedCredentialWorksAcrossServiceInstancesAndFailsClosedWhenRevokedOrDisabled()throws Exception {
        try(var browser=new Browser()) {
            var enrolled=identities.register(registration(browser.token(browser.account(true),1).registrationToken()));
            var restarted=new WorkerIdentityService(db,Clock.systemUTC());
            assertThat(restarted.authenticate(enrolled.workerCredential())).isEqualTo(enrolled.worker().workerId());
            db.update("update workers set status='DISABLED' where id=?",enrolled.worker().workerId());
            assertThatThrownBy(()->restarted.authenticate(enrolled.workerCredential())).isInstanceOf(AuthException.class);
            db.update("update workers set status='STARTING' where id=?",enrolled.worker().workerId());
            db.update("update worker_credentials set revoked_at=? where worker_id=?",java.sql.Timestamp.from(Instant.now()),enrolled.worker().workerId());
            assertThat(browser.call("/internal/workers/"+enrolled.worker().workerId()+"/identity","GET",null,enrolled.workerCredential(),false).status()).isEqualTo(401);
        }
    }
}
