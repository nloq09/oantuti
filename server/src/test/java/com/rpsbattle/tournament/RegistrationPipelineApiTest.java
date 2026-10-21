package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registration.*;
import com.rpsbattle.tournament.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationPipelineApiTest extends StageThreeWebFixture {
    @MockitoBean Clock clock;
    static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
    @BeforeEach void time(){when(clock.instant()).thenReturn(START.plusSeconds(30));when(clock.getZone()).thenReturn(ZoneOffset.UTC);}
    String cup(String policy,boolean verify,boolean open)throws Exception{return cup(policy,verify,open,10);}
    String cup(String policy,boolean verify,boolean open,int capacity)throws Exception{
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Registration pipeline","maxParticipants",capacity,"botPolicy",Map.of("submissionMode","BOTH"),"registrationPolicy",Map.of("approvalPolicy",policy,"requireAffiliationVerification",verify)),201);
        String base="/api/tournaments/"+created.path("id").asText();var timeline=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(180),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);
        request(base+"/timeline","PUT",Map.of("revision",created.path("revision").asLong(),"timeline",timeline),200);if(open)request(base+"/open-registration","POST",Map.of(),200);return base;
    }
    Map<String,Object> signup(){return Map.of("rulesAccepted",true,"intendedLanguage","CPP","affiliation","UET");}
    long revision(String base)throws Exception{return request(base,"GET",null,200).path("revision").asLong();}

    String applicant()throws Exception{String username="capacity_"+UUID.randomUUID().toString().substring(0,8);registration.register(username,username+"@example.com","Capacity Test Password",username);loginApplicant(username);return username;}
    void loginApplicant(String username)throws Exception{request("/api/auth/login","POST",Map.of("username",username,"password","Capacity Test Password"),200);}
    void loginOwner()throws Exception{request("/api/auth/login","POST",Map.of("username",account.username(),"password","Tournament Test Password"),200);}
    @Test void fullAutoApprovalCreatesPrivateOrderedWaitlistAndRejoiningGoesToTheTail()throws Exception{
        String base=cup("HYBRID",false,true,2);request(base+"/participant-registration","POST",signup(),200);applicant();request(base+"/participant-registration","POST",signup(),200);
        String first=applicant();var waiting=request(base+"/participant-registration","POST",signup(),200);assertThat(waiting.path("registrationStatus").asText()).isEqualTo("WAITLISTED");assertThat(waiting.path("registration").path("waitlistPosition").asLong()).isEqualTo(1);assertThat(waiting.toString()).doesNotContain("CAPACITY_FULL",account.username());
        String second=applicant();assertThat(request(base+"/participant-registration","POST",signup(),200).path("registration").path("waitlistPosition").asLong()).isEqualTo(2);request(base+"/participant-applications?status=WAITLISTED","GET",null,403);
        loginApplicant(first);request(base+"/participant-registration","DELETE",Map.of(),200);loginApplicant(second);assertThat(request(base+"/participant-flow","GET",null,200).path("registration").path("waitlistPosition").asLong()).isEqualTo(1);
        loginApplicant(first);assertThat(request(base+"/participant-registration","POST",signup(),200).path("registration").path("waitlistPosition").asLong()).isEqualTo(2);
        loginOwner();var list=request(base+"/participant-applications?status=WAITLISTED","GET",null,200).path("items");assertThat(list.get(0).path("displayName").asText()).isEqualTo(second);assertThat(list.get(0).path("reasonCodes").get(0).asText()).isEqualTo("CAPACITY_FULL");var counts=request(base+"/organizer-metrics","GET",null,200).path("registration");assertThat(counts.path("approved").asInt()).isEqualTo(2);assertThat(counts.path("waitlisted").asInt()).isEqualTo(2);assertThat(counts.path("remainingSlots").asInt()).isZero();
    }
    @Test void pendingReviewDoesNotReserveSlotsAndManualApprovalCannotExceedCapacity()throws Exception{
        String base=cup("MANUAL_REVIEW",false,true,2);String ownerApplication=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();applicant();String a=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();applicant();String b=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();
        loginOwner();for(String id:List.of(ownerApplication,a))request(base+"/participant-applications/"+id+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","Accept applicant"),200);
        var error=request(base+"/participant-applications/"+b+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","No remaining place"),409);assertThat(error.toString()).contains("CAPACITY_FULL");assertThat(request(base+"/participant-applications?status=NEEDS_REVIEW","GET",null,200).path("items").size()).isEqualTo(1);
        request(base+"/participant-applications/"+b+"/review","POST",Map.of("revision",revision(base),"decision","WAITLISTED","reason","Wait for a place"),200);request(base+"/participant-registration","DELETE",Map.of(),200);
        request(base+"/participant-applications/"+b+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","A place is now available"),200);assertThat(request(base+"/organizer-metrics","GET",null,200).path("registration").path("approved").asInt()).isEqualTo(2);
    }
    @Test void autoAndHybridReturnFinalApprovalWithoutPendingAndPreserveCodingWindow()throws Exception{
        for(String policy:List.of("AUTO_APPROVE","HYBRID")){
            String base=cup(policy,false,true);var flow=request(base+"/participant-registration","POST",signup(),200);
            assertThat(flow.path("registrationStatus").asText()).isEqualTo("APPROVED");assertThat(flow.path("nextAction").asText()).isEqualTo("WAIT_FOR_CODING");assertThat(flow.path("canModifyWorkspace").asBoolean()).isFalse();
            request(base+"/workspace","POST",Map.of("language","CPP"),409);request(base+"/participant-registration","POST",signup(),409);
            when(clock.instant()).thenReturn(START.plusSeconds(120));assertThat(request(base+"/participant-flow","GET",null,200).path("nextAction").asText()).isEqualTo("OPEN_CODE_WORKSPACE");when(clock.instant()).thenReturn(START.plusSeconds(30));
        }
    }
    @Test void manualAndHybridExceptionsExposeNeedsReviewAndOrganizerOnlyReasons()throws Exception{
        for(String policy:List.of("MANUAL_REVIEW","HYBRID")){
            String base=cup(policy,policy.equals("HYBRID"),true);var flow=request(base+"/participant-registration","POST",signup(),200);
            assertThat(flow.path("registrationStatus").asText()).isEqualTo("NEEDS_REVIEW");assertThat(flow.path("participantStatus").asText()).isEqualTo("REGISTRATION_NEEDS_REVIEW");assertThat(flow.path("registration").has("reasonCodes")).isFalse();
            var item=request(base+"/participant-applications?status=NEEDS_REVIEW","GET",null,200).path("items").get(0);
            for(String path:List.of("","/summary","/export"))assertThat(request(base+path,"GET",null,200).toString()).doesNotContain("AFFILIATION_NOT_VERIFIED","MANUAL_REVIEW_REQUIRED");
            assertThat(item.path("status").asText()).isEqualTo("NEEDS_REVIEW");assertThat(item.path("reasonCodes").get(0).asText()).isEqualTo(policy.equals("HYBRID")?"AFFILIATION_NOT_VERIFIED":"MANUAL_REVIEW_REQUIRED");
            assertThat(request(base+"/participant-applications?status=PENDING","GET",null,200).path("items").size()).isEqualTo(1);
            request(base+"/participant-applications/"+item.path("id").asText()+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","Verified application"),200);
            assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("APPROVED");
        }
    }
    @Test void requiredAffiliationAndLanguageCannotBeBypassed()throws Exception{
        String base=cup("HYBRID",true,true);request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","CPP"),400);
        request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","RUST","affiliation","UET"),400);
        assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("NOT_REGISTERED");
    }
    @Test void policyUpdateRequiresManagerReasonRevisionAndDraftAndIsAudited()throws Exception{
        String base=cup("MANUAL_REVIEW",false,false);long before=revision(base);var update=new HashMap<String,Object>(Map.of("revision",before,"policy",Map.of("approvalPolicy","HYBRID","requireAffiliationVerification",true),"reason","Use exception review"));
        update.put("revision",before-1);request(base+"/registration-policy","PUT",update,409);update.put("revision",before);update.put("reason","");request(base+"/registration-policy","PUT",update,400);update.put("reason","Use exception review");request(base+"/registration-policy","PUT",update,200);
        var detail=request(base,"GET",null,200);assertThat(detail.path("registrationPolicy").path("approvalPolicy").asText()).isEqualTo("HYBRID");assertThat(detail.path("revision").asLong()).isGreaterThan(before);
        var t=tournaments.find(base.substring(base.lastIndexOf('/')+1));assertThat(t.operations.audit()).anyMatch(e->e.type().equals("REGISTRATION_POLICY_UPDATED")&&e.reason().equals("Use exception review"));
        request(base+"/open-registration","POST",Map.of(),200);update.put("revision",revision(base));request(base+"/registration-policy","PUT",update,409);
        String user="pipeline_"+UUID.randomUUID().toString().substring(0,8);registration.register(user,user+"@example.com","Pipeline Test Password","Other player");request("/api/auth/login","POST",Map.of("username",user,"password","Pipeline Test Password"),200);
        request(base+"/registration-policy","PUT",update,403);request(base+"/participant-applications","GET",null,403);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("NOT_REGISTERED");
    }
    @Test void policySurvivesJsonSnapshotsAndOldSnapshotsKeepManualReview()throws Exception{
        var t=new Tournament(UUID.randomUUID().toString(),"Policy snapshot",TournamentFormat.ROUND_ROBIN,START);t.registrationPolicy=new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,true);
        var converter=new TournamentDetailsConverter();var encoded=converter.convertToDatabaseColumn(TournamentDetails.snapshot(t));var restored=new Tournament(t.id,t.name,t.format,START);converter.convertToEntityAttribute(encoded).restore(restored);assertThat(restored.registrationPolicy).isEqualTo(t.registrationPolicy);
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(encoded);old.remove("registrationPolicy");converter.convertToEntityAttribute(old.toString()).restore(restored);assertThat(restored.registrationPolicy).isEqualTo(RegistrationPolicy.legacy());
    }
}
