package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.timeline.*;
import com.rpsbattle.auth.AuthService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimelineRegistrationApiTest extends StageThreeWebFixture {
    @MockitoBean Clock clock;
    @Autowired TimelineRegistrationStore applications;
    @Autowired OwnedTournamentService ownerService;
    static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
    @BeforeEach void serverTime(){when(clock.instant()).thenReturn(START.plusSeconds(30));when(clock.getZone()).thenReturn(ZoneOffset.UTC);}
    TournamentTimeline timeline(){return new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(180),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);}
    String cup(String mode,int limit)throws Exception{loginAccount();var t=request("/api/tournaments","POST",Map.of("name","Scheduled applications","maxParticipants",limit,"botPolicy",Map.of("submissionMode",mode)),201);String base="/api/tournaments/"+t.path("id").asText();request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",timeline()),200);request(base+"/open-registration","POST",Map.of(),200);return base;}
    Map<String,Object> signup(){return Map.of("rulesAccepted",true,"intendedLanguage","CPP","affiliation","UET");}
    @Test void personCanRegisterWithoutBotInAllThreeModesAndNoExecutionRosterIsCreated()throws Exception{
        for(String mode:List.of("PRE_UPLOADED_ONLY","CODE_IN_PLATFORM_ONLY","BOTH")){
            String base=cup(mode,2);var result=request(base+"/participant-registration","POST",signup(),200);assertThat(result.path("registrationStatus").asText()).isEqualTo("NEEDS_REVIEW");assertThat(result.path("registration").path("intendedLanguage").asText()).isEqualTo("CPP");assertThat(result.path("registration").has("botId")).isFalse();
            var t=request(base,"GET",null,200);assertThat(t.path("participants").size()).isZero();assertThat(t.path("registrations").size()).isZero();assertThat(t.path("audit").toString()).contains("PARTICIPANT_APPLICATION_SUBMITTED");
            assertThat(request(base+"/participant-flow","GET",null,200).path("nextAction").asText()).isEqualTo("WAIT_FOR_APPROVAL");request(base+"/participant-registration","POST",signup(),409);
        }
    }
    @Test void exactOpenAndCloseUseServerClockAndCannotBeBypassedByLegacyRoutes()throws Exception{
        String base=cup("BOTH",2);when(clock.instant()).thenReturn(START.minusNanos(1));request(base+"/participant-registration","POST",signup(),409);
        when(clock.instant()).thenReturn(START);request(base+"/participant-registration","POST",signup(),200);
        for(String type:List.of("BOT","HUMAN"))request(base+"/registrations","POST",Map.of("controllerType",type,"rulesAccepted",true,"botId",UUID.randomUUID().toString()),type.equals("HUMAN")?400:409);
        request(base+"/registrations","POST",Map.of("controllerType","HUMAN","rulesAccepted",true),409);
        request(base+"/participants","POST",Map.of("botId",UUID.randomUUID().toString()),409);
        when(clock.instant()).thenReturn(START.plusSeconds(60));request(base+"/participant-registration","POST",signup(),409);request(base+"/participant-registration","DELETE",Map.of(),409);assertThat(request(base+"/participant-flow","GET",null,200).path("phase").asText()).isEqualTo("REGISTRATION_REVIEW");
    }
    @Test void directWorkspaceSourceBuildTestImportAndSubmitAreClosedDuringRegistration()throws Exception{
        String base=cup("BOTH",2);request(base+"/participant-registration","POST",signup(),200);
        request(base+"/workspace","POST",Map.of("language","CPP"),409);request(base+"/workspace/me?language=CPP","GET",null,409);request(base+"/workspace/me","PUT",Map.of("language","CPP","revision",0,"sourceCode","source"),409);
        for(String job:List.of("build","test","test-match","validate"))request(base+"/workspace/me/"+job,"POST",Map.of("language","CPP","revision",0,"sourceCode","source"),409);
        request(base+"/workspace/public-tests","GET",null,409);request(base+"/submission/preuploaded","POST",Map.of("botId",UUID.randomUUID().toString()),409);request(base+"/workspace/me/submit","POST",Map.of("versionId",UUID.randomUUID().toString(),"revision",0,"rulesAccepted",true),409);
        when(clock.instant()).thenReturn(START.plusSeconds(120));request(base+"/workspace","POST",Map.of("language","CPP"),403);
    }
    @Test void validationPrivacyCapacityAndWithdrawalAreAccountScoped()throws Exception{
        String base=cup("CODE_IN_PLATFORM_ONLY",2);request(base+"/participant-registration","POST",Map.of("rulesAccepted",false,"intendedLanguage","CPP"),400);request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","RUST"),400);
        for(String field:List.of("botId","sourceCode","versionId")){var body=new HashMap<String,Object>(signup());body.put(field,"untrusted");request(base+"/participant-registration","POST",body,400);}
        var a=request(base+"/participant-registration","POST",signup(),200).path("registration");request(base+"/participant-registration","DELETE",Map.of(),200);var renewed=request(base+"/participant-registration","POST",signup(),200).path("registration");assertThat(renewed.path("id")).isEqualTo(a.path("id"));
        for(int i=0;i<2;i++){String username="ap_"+UUID.randomUUID().toString().substring(0,8);registration.register(username,username+"@example.com","Applicant Test Password","Applicant "+i);request("/api/auth/login","POST",Map.of("username",username,"password","Applicant Test Password"),200);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("NOT_REGISTERED");request(base+"/participant-registration","POST",signup(),i==0?200:409);}
        assertThat(applications.mine(UUID.fromString(base.substring(base.lastIndexOf('/')+1)),account.id().value())).isNotNull();
    }
}
