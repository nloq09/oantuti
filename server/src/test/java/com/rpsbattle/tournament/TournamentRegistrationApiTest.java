package com.rpsbattle.tournament;

import org.junit.jupiter.api.Test;
import com.rpsbattle.account.domain.User;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TournamentRegistrationApiTest extends StageThreeWebFixture {
    @org.junit.jupiter.api.BeforeEach void needsPythonSandbox() {org.junit.jupiter.api.Assumptions.assumeTrue(uploads.available(com.rpsbattle.bot.upload.BotLanguage.PYTHON));}
    @org.springframework.beans.factory.annotation.Autowired OwnedTournamentService registrationManager;
    private void signIn(User user) throws Exception {account=user;request("/api/auth/login","POST",Map.of("username",user.username(),"password","Tournament Test Password"),200);}
    @Test void approvalAndCheckInUseSeatOwnerAndExcludeMissingCheckInFromReadyRoster() throws Exception {
        loginAccount();var owner=account;var a=upload("Alpha");var absent=upload("Absent");
        var cup=request("/api/tournaments","POST",Map.of("name","Check-in Cup","checkInRequired",true,"maxParticipants",3),201);
        String path="/api/tournaments/"+cup.path("id").asText();
        request(path+"/registrations","POST",Map.of("botId",a.id()),400);
        request(path+"/start","POST",Map.of(),400);request(path+"/open-registration","POST",Map.of(),200);
        request(path+"/registrations","POST",Map.of("botId",a.id()),200);request(path+"/registrations","POST",Map.of("botId",absent.id()),200);
        assertThat(owned.require(com.rpsbattle.auth.AuthService.sessionAuthentication(account),a.id()).status()).isEqualTo(com.rpsbattle.bot.ownership.BotMetadata.Status.READY);
        request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();var guest=account;var b=upload("Beta");
        request(path+"/registrations","POST",Map.of("botId",a.id()),403);
        var pending=request(path+"/participants","POST",Map.of("botId",b.id(),"ownerUserId",owner.id().value()),200);
        assertThat(pending.path("participants").size()).isZero();assertThat(pending.path("registrations").size()).isEqualTo(3);
        String aId=pending.path("registrations").get(0).path("id").asText(),bId=pending.path("registrations").get(2).path("id").asText();
        request(path+"/registrations","POST",Map.of("botId",b.id()),400);
        request(path+"/registrations","POST",Map.of("botId",upload("Over capacity").id()),400);
        request(path+"/registrations/"+aId+"/approve","POST",Map.of(),403);
        request(path+"/check-in","POST",Map.of(),400);
        signIn(owner);
        for(var r:pending.path("registrations"))request(path+"/registrations/"+r.path("id").asText()+"/approve","POST",Map.of(),200);
        request(path+"/registrations/"+aId+"/approve","POST",Map.of(),400);
        var checking=request(path+"/close-registration","POST",Map.of(),200);assertThat(checking.path("status").asText()).isEqualTo("CHECK_IN");
        request(path+"/close-check-in","POST",Map.of(),400);request(path+"/start","POST",Map.of(),400);
        request(path+"/check-in","POST",Map.of("registrationId",bId),403);
        request(path+"/check-in","POST",Map.of("registrationId",aId),200);
        var repeat=request(path+"/check-in","POST",Map.of("registrationId",aId),200);
        var original=repeat.path("registrations").get(0).path("checkedInAt").asText();
        assertThat(request(path+"/check-in","POST",Map.of("registrationId",aId),200).path("registrations").get(0).path("checkedInAt").asText()).isEqualTo(original);
        signIn(guest);request(path+"/check-in","POST",Map.of(),200);request(path+"/close-check-in","POST",Map.of(),403);
        signIn(owner);var ready=request(path+"/close-check-in","POST",Map.of(),200);
        assertThat(ready.path("status").asText()).isEqualTo("READY");assertThat(ready.path("participants").size()).isEqualTo(2);
        assertThat(ready.path("registrations").toString()).contains("NOT_CHECKED_IN");assertThat(ready.path("matches").size()).isZero();
        request(path+"/registrations/"+aId,"DELETE",null,400);request(path+"/check-in","POST",Map.of(),400);
    }
    @Test void rejectWithdrawAndReregisterRetainIdentityAndCannotApproveAnotherTournamentRegistration() throws Exception {
        loginAccount();var bot=upload("Retry");
        String path="/api/tournaments/"+request("/api/tournaments","POST",Map.of("name","Approval"),201).path("id").asText();
        request(path+"/open-registration","POST",Map.of(),200);
        var registered=request(path+"/registrations","POST",Map.of("botId",bot.id()),200);
        String id=registered.path("registrations").get(0).path("id").asText();
        String other="/api/tournaments/"+request("/api/tournaments","POST",Map.of("name","Other"),201).path("id").asText();request(other+"/open-registration","POST",Map.of(),200);
        request(other+"/registrations/"+id+"/approve","POST",Map.of(),400);
        request(path+"/registrations/"+id+"/reject","POST",Map.of(),200);
        request(path+"/registrations/"+id+"/approve","POST",Map.of(),400);
        var retried=request(path+"/registrations","POST",Map.of("botId",bot.id()),200);assertThat(retried.path("registrations").get(0).path("id").asText()).isEqualTo(id);
        request(path+"/registrations/"+id,"DELETE",null,200);request(path+"/registrations/"+id,"DELETE",null,400);
        request(path+"/registrations","POST",Map.of("botId",bot.id()),200);request(path+"/registrations/"+id+"/approve","POST",Map.of(),200);
        var rejected=request(path+"/registrations/"+id+"/reject","POST",Map.of(),200);assertThat(rejected.path("participants").size()).isZero();
        request("/api/tournaments","POST",Map.of("name","Invalid","maxParticipants",1),400);
    }
    @Test void concurrentRegistrationsCannotExceedCapacityAndMissingOrDeletedBotsCannotRegister() throws Exception {
        loginAccount();var a=upload("First");var b=upload("Second");var c=upload("Third");var missing=upload("Missing");var deleted=upload("Deleted");
        String id=request("/api/tournaments","POST",Map.of("name","Capacity","maxParticipants",2),201).path("id").asText(),path="/api/tournaments/"+id;
        request(path+"/open-registration","POST",Map.of(),200);
        java.nio.file.Files.delete(uploads.source(uploads.stored(missing.id())));
        request(path+"/registrations","POST",Map.of("botId",missing.id()),400);
        owned.delete(com.rpsbattle.auth.AuthService.sessionAuthentication(account),deleted.id());request(path+"/registrations","POST",Map.of("botId",deleted.id()),404);
        request(path+"/registrations","POST",Map.of("botId",a.id()),200);
        var auth=com.rpsbattle.auth.AuthService.sessionAuthentication(account);var successes=new java.util.concurrent.atomic.AtomicInteger();var full=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(var bot:java.util.List.of(b,c))results.add(executor.submit(()->{try{registrationManager.register(auth,id,bot.id());successes.incrementAndGet();}catch(com.rpsbattle.online.DemoRequestException error){assertThat(error.getMessage()).isEqualTo("PARTICIPANT_LIMIT");full.incrementAndGet();}}));
            for(var result:results)result.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1);assertThat(full.get()).isEqualTo(1);
        var result=request(path,"GET",null,200);assertThat(result.path("registrations").size()).isEqualTo(2);assertThat(result.path("participants").size()).isZero();
    }
}
