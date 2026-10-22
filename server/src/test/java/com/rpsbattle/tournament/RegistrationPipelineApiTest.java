package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registration.*;
import com.rpsbattle.tournament.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@org.springframework.context.annotation.Import(RegistrationPipelineApiTest.TestTime.class)
class RegistrationPipelineApiTest extends StageThreeWebFixture {
    @org.springframework.beans.factory.annotation.Autowired TestClock clock;
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TestTime {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        TestClock registrationClock(){return new TestClock();}
    }
    static final class TestClock extends Clock {
        private volatile Instant now=Instant.parse("2026-10-19T00:00:30Z");
        void at(Instant instant){now=instant;}
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
    }
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.coding.CodingDeadlineService codingDeadlines;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.coding.FinalValidationService finalValidations;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate database;
    static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
    @BeforeEach void time(){clock.at(START.plusSeconds(30));}
    String cup(String policy,boolean verify,boolean open)throws Exception{return cup(policy,verify,open,10);}
    String cup(String policy,boolean verify,boolean open,int capacity)throws Exception{
        return cup(policy,verify,open,capacity,"ROUND_ROBIN");
    }
    String cup(String policy,boolean verify,boolean open,int capacity,String format)throws Exception{
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Registration pipeline","maxParticipants",capacity,"format",format,"botPolicy",Map.of("submissionMode","BOTH"),"registrationPolicy",Map.of("approvalPolicy",policy,"requireAffiliationVerification",verify)),201);
        String base="/api/tournaments/"+created.path("id").asText();var timeline=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(180),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);
        request(base+"/timeline","PUT",Map.of("revision",created.path("revision").asLong(),"timeline",timeline),200);if(open)request(base+"/open-registration","POST",Map.of(),200);return base;
    }
    Map<String,Object> signup(){return Map.of("rulesAccepted",true,"intendedLanguage","CPP","affiliation","UET");}
    long revision(String base)throws Exception{return request(base,"GET",null,200).path("revision").asLong();}

    String applicant()throws Exception{String username="capacity_"+UUID.randomUUID().toString().substring(0,8);registration.register(username,username+"@example.com","Capacity Test Password",username);loginApplicant(username);return username;}
    void loginApplicant(String username)throws Exception{request("/api/auth/login","POST",Map.of("username",username,"password","Capacity Test Password"),200);}
    void loginOwner()throws Exception{request("/api/auth/login","POST",Map.of("username",account.username(),"password","Tournament Test Password"),200);}
    @Test void playersReceiveOnlyTheirPublicReviewMessageWhileManagersRetainPrivateReasons()throws Exception{
        String base=cup("MANUAL_REVIEW",false,true);var applied=request(base+"/participant-registration","POST",signup(),200).path("registration");String id=applied.path("id").asText();
        request(base+"/participant-applications/"+id+"/review","POST",Map.of("revision",revision(base),"decision","REJECTED","reason","Private security/admin note","publicMessage","Please confirm your school details"),200);
        var mine=request(base+"/participant-flow","GET",null,200);assertThat(mine.path("registration").path("publicMessage").asText()).isEqualTo("Please confirm your school details");assertThat(mine.toString()).doesNotContain("Private security/admin note","reviewReason","reasonCodes");
        assertThat(request(base+"/participant-applications/"+id,"GET",null,200).toString()).contains("Private security/admin note","Please confirm your school details");
        for(String path:List.of("/notifications","","/summary","/export"))assertThat(request(base+path,"GET",null,200).toString()).doesNotContain("Private security/admin note");
        request(base+"/participant-applications/"+id+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","Verified privately"),200);
        assertThat(request(base+"/participant-flow","GET",null,200).path("registration").path("publicMessage").isNull()).isTrue();
        applicant();request(base+"/participant-registration","POST",signup(),200);assertThat(request(base+"/participant-flow","GET",null,200).toString()).doesNotContain("Please confirm your school details","Private security/admin note");assertThat(request(base+"/notifications","GET",null,200).toString()).doesNotContain(id,"Private security/admin note");
    }
    @Test void bulkPublicMessagesAreSeparateAndValidatedBeforeAnyRowChanges()throws Exception{
        String base=cup("MANUAL_REVIEW",false,true);var application=request(base+"/participant-registration","POST",signup(),200).path("registration");var entry=Map.of("id",application.path("id").asText(),"status",application.path("status").asText(),"registeredAt",application.path("registeredAt").asText());
        for(String message:List.of("x".repeat(501),"bad"+(char)0+"text"))request(base+"/participant-applications/bulk-review","POST",Map.of("revision",revision(base),"decision","REJECTED","reason","Internal note","publicMessage",message,"items",List.of(entry)),400);
        assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("NEEDS_REVIEW");
        request(base+"/participant-applications/bulk-review","POST",Map.of("revision",revision(base),"decision","REJECTED","reason","Internal note","publicMessage","The published entry requirements are not met","items",List.of(entry)),200);
        var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("registration").path("publicMessage").asText()).isEqualTo("The published entry requirements are not met");assertThat(flow.toString()).doesNotContain("Internal note");
    }
    @Test void submissionAndWithdrawalNotificationsArePersonalStableAndReadStatePersists()throws Exception{
        String base=cup("HYBRID",false,true);request(base+"/participant-registration","POST",signup(),200);
        var feed=request(base+"/notifications","GET",null,200);var submitted=java.util.stream.StreamSupport.stream(feed.path("items").spliterator(),false).filter(n->n.path("type").asText().equals("PARTICIPANT_APPLICATION_SUBMITTED")).toList();assertThat(submitted).hasSize(1);assertThat(submitted.getFirst().path("state").asText()).isEqualTo("APPROVED");assertThat(feed.toString()).doesNotContain("reasonCodes","reviewReason");
        request(base+"/notifications/read","POST",Map.of("through",feed.path("through").asLong()),200);var read=request(base+"/notifications","GET",null,200);assertThat(java.util.stream.StreamSupport.stream(read.path("items").spliterator(),false).filter(n->n.path("id").equals(submitted.getFirst().path("id"))).findFirst().orElseThrow().path("read").asBoolean()).isTrue();
        request(base+"/participant-registration","DELETE",Map.of(),200);assertThat(request(base+"/notifications","GET",null,200).toString()).contains("PARTICIPANT_APPLICATION_WITHDRAWN");assertThat(request(base+"/participant-flow","GET",null,200).path("canWithdraw").asBoolean()).isFalse();
        applicant();request(base+"/participant-registration","POST",signup(),200);assertThat(request(base+"/notifications","GET",null,200).toString()).doesNotContain("PARTICIPANT_APPLICATION_WITHDRAWN");
    }
    @Test void playerWithdrawalCapabilityUsesServerDeadlineAndExplicitRosterLock()throws Exception{
        String base=cup("HYBRID",false,true,2);request(base+"/participant-registration","POST",signup(),200);applicant();request(base+"/participant-registration","POST",signup(),200);loginOwner();request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Close admission"),200);
        assertThat(request(base+"/participant-flow","GET",null,200).path("canWithdraw").asBoolean()).isTrue();request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Lock roster"),200);assertThat(request(base+"/participant-flow","GET",null,200).path("canWithdraw").asBoolean()).isFalse();
        String pending=cup("MANUAL_REVIEW",false,true);request(pending+"/participant-registration","POST",signup(),200);clock.at(START.plusSeconds(120));assertThat(request(pending+"/participant-flow","GET",null,200).path("canWithdraw").asBoolean()).isFalse();request(pending+"/participant-registration","DELETE",Map.of(),409);
    }
    @Test void dashboardPolicyAndReviewPermissionsFollowLifecycle()throws Exception{
        String base=cup("HYBRID",true,false);
        var draft=request(base+"/registration-summary","GET",null,200);
        assertThat(draft.path("capacity").asInt()).isEqualTo(10);
        assertThat(draft.path("remainingSlots").asLong()).isEqualTo(10);
        assertThat(draft.path("policy").path("approvalPolicy").asText()).isEqualTo("HYBRID");
        assertThat(draft.path("policy").path("requireAffiliationVerification").asBoolean()).isTrue();
        assertThat(draft.path("canEditPolicy").asBoolean()).isTrue();
        assertThat(draft.path("canReview").asBoolean()).isFalse();
        request(base+"/open-registration","POST",Map.of(),200);
        var opened=request(base+"/registration-summary","GET",null,200);
        assertThat(opened.path("canEditPolicy").asBoolean()).isFalse();
        assertThat(opened.path("canReview").asBoolean()).isTrue();
        request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Review existing applications"),200);
        assertThat(request(base+"/registration-summary","GET",null,200).path("canReview").asBoolean()).isTrue();
        clock.at(START.plusSeconds(120));
        assertThat(request(base+"/registration-summary","GET",null,200).path("canReview").asBoolean()).isFalse();
        applicant();request(base+"/registration-summary","GET",null,403);
    }
    @Test void dashboardAggregatesThousandsOfApplicationsWithoutReturningTheirDetails()throws Exception{
        String base=cup("HYBRID",false,true,4000,"SWISS");
        UUID tournament=UUID.fromString(base.substring(base.lastIndexOf('/')+1));
        var users=new ArrayList<Object[]>();var applications=new ArrayList<Object[]>();
        for(int i=0;i<3842;i++){
            UUID user=UUID.randomUUID();String username="dash_"+user.toString().replace("-","").substring(0,24);
            users.add(new Object[]{user,username,username+"@example.com","unused-test-hash",username});
            String status=i<3701?"APPROVED":i<3725?"PENDING":i<3830?"WAITLISTED":"REJECTED";
            applications.add(new Object[]{UUID.randomUUID(),tournament,user,username,"CPP",status,java.sql.Timestamp.from(START),java.sql.Timestamp.from(START),status.equals("WAITLISTED")?(long)(i-3724):null});
        }
        database.batchUpdate("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",users);
        database.batchUpdate("insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,status,registered_at,rules_accepted_at,waitlist_order) values(?,?,?,?,?,?,?,?,?)",applications);
        var summary=request(base+"/registration-summary","GET",null,200);
        assertThat(summary.path("counts").path("total").asInt()).isEqualTo(3842);
        assertThat(summary.path("counts").path("approved").asInt()).isEqualTo(3701);
        assertThat(summary.path("counts").path("needsReview").asInt()).isEqualTo(24);
        assertThat(summary.path("counts").path("waitlisted").asInt()).isEqualTo(105);
        assertThat(summary.path("counts").path("rejected").asInt()).isEqualTo(12);
        assertThat(summary.path("remainingSlots").asInt()).isEqualTo(299);
        assertThat(summary.has("items")).isFalse();
        assertThat(summary.toString()).doesNotContain("@example.com","unused-test-hash","dash_");
        assertThat(request(base+"/participant-applications?status=NEEDS_REVIEW","GET",null,200).path("items").size()).isEqualTo(24);
        assertThat(request(base+"/participant-applications","GET",null,200).path("items").size()).isEqualTo(25);
    }
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
    @Test void closeAllowsReviewAndWithdrawalPromotionButLockFixesRosterAndNotifiesOnlyPromotedPlayer()throws Exception{
        String base=cup("HYBRID",false,true,2);request(base+"/participant-registration","POST",signup(),200);String a=applicant();request(base+"/participant-registration","POST",signup(),200);String b=applicant();request(base+"/participant-registration","POST",signup(),200);String c=applicant();String waiting=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();
        loginOwner();request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Close new applications"),200);assertThat(request(base,"GET",null,200).path("acceptingRegistrations").asBoolean()).isFalse();assertThat(request(base+"/registration-summary","GET",null,200).path("control").path("lockedAt").isNull()).isTrue();request(base+"/participant-registration","DELETE",Map.of(),200);
        loginApplicant(b);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("APPROVED");var notifications=request(base+"/notifications","GET",null,200).path("items");assertThat(java.util.stream.StreamSupport.stream(notifications.spliterator(),false).filter(n->n.path("type").asText().equals("WAITLIST_PROMOTED")).count()).isEqualTo(1);
        loginApplicant(c);assertThat(request(base+"/notifications","GET",null,200).toString()).doesNotContain("WAITLIST_PROMOTED");request(base+"/registration-summary","GET",null,403);applicant();request(base+"/participant-registration","POST",signup(),409);
        loginOwner();var locked=request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Finalize roster"),200);assertThat(locked.path("control").path("lockedCount").asInt()).isEqualTo(2);assertThat(locked.path("control").path("lockedByName").asText()).isEqualTo(account.displayName());
        request(base+"/participant-applications/"+waiting+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","Too late"),409);loginApplicant(b);request(base+"/participant-registration","DELETE",Map.of(),409);
        clock.at(START.plusSeconds(120));assertThat(request(base+"/participant-flow","GET",null,200).path("nextAction").asText()).isEqualTo("OPEN_CODE_WORKSPACE");
    }
    @Test void unresolvedReviewBlocksRosterLockUntilOrganizerResolvesIt()throws Exception{
        String base=cup("MANUAL_REVIEW",false,true,2);String first=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();applicant();String second=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();applicant();String pending=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();loginOwner();
        for(String id:List.of(first,second))request(base+"/participant-applications/"+id+"/review","POST",Map.of("revision",revision(base),"decision","APPROVED","reason","Confirm participant"),200);
        request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Close new applications"),200);assertThat(request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Attempt lock"),409).toString()).contains("ROSTER_REVIEW_REQUIRED");var summary=request(base+"/registration-summary","GET",null,200);assertThat(summary.path("counts").path("needsReview").asInt()).isEqualTo(1);assertThat(summary.path("canLock").asBoolean()).isFalse();
        request(base+"/participant-applications/"+pending+"/review","POST",Map.of("revision",revision(base),"decision","REJECTED","reason","Not eligible"),200);request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","All reviews resolved"),200);
    }
    @Test void deadlineClosesAdmissionButDoesNotSilentlyLockOrOpenCoding()throws Exception{
        String base=cup("HYBRID",false,true,2);request(base+"/participant-registration","POST",signup(),200);String other=applicant();request(base+"/participant-registration","POST",signup(),200);loginOwner();clock.at(START.plusSeconds(60));assertThat(request(base+"/registration-summary","GET",null,200).path("registrationOpen").asBoolean()).isFalse();assertThat(request(base+"/participant-flow","GET",null,200).path("phase").asText()).isEqualTo("REGISTRATION_REVIEW");
        clock.at(START.plusSeconds(120));request(base+"/workspace","POST",Map.of("language","CPP"),409);var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("phase").asText()).isEqualTo("REGISTRATION_REVIEW");assertThat(flow.path("canModifyWorkspace").asBoolean()).isFalse();assertThat(request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Missed coding opening"),409).toString()).contains("ROSTER_LOCK_DEADLINE");
    }
    @Test void explicitWaitlistDecisionDoesNotImmediatelyUndoItselfThroughPromotion()throws Exception{
        String base=cup("HYBRID",false,true,2);String application=request(base+"/participant-registration","POST",signup(),200).path("registration").path("id").asText();request(base+"/participant-applications/"+application+"/review","POST",Map.of("revision",revision(base),"decision","WAITLISTED","reason","Defer this applicant"),200);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("WAITLISTED");
    }
    @Test void unlockedRosterCannotFreezeValidateOrStartEvenAfterAllDeadlines()throws Exception{
        String base=cup("HYBRID",false,true,2);request(base+"/participant-registration","POST",signup(),200);UUID id=UUID.fromString(base.substring(base.lastIndexOf('/')+1));clock.at(START.plusSeconds(200));codingDeadlines.processDeadlines();finalValidations.process(id.toString());
        assertThat(database.queryForObject("select count(*) from tournament_coding_freezes where tournament_id=?",Long.class,id)).isZero();assertThat(database.queryForObject("select count(*) from tournament_final_validations where tournament_id=?",Long.class,id)).isZero();var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("phase").asText()).isEqualTo("REGISTRATION_REVIEW");assertThat(flow.path("submissionStatus").asText()).isEqualTo("NO_SUBMISSION");clock.at(START.plusSeconds(300));assertThat(request(base+"/start","POST",Map.of(),409).toString()).contains("ROSTER_NOT_LOCKED");
    }
    @Test void closeAndRosterLockRequireManagerFreshRevisionAndReason()throws Exception{
        String base=cup("HYBRID",false,true,2);long revision=revision(base);request(base+"/registration/close","POST",Map.of("revision",revision-1,"reason","Stale close"),409);request(base+"/registration/close","POST",Map.of("revision",revision,"reason",""),400);applicant();
        for(String path:List.of("/registration/close","/roster/lock"))request(base+path,"POST",Map.of("revision",revision,"reason","Unauthorized"),403);
        loginOwner();request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Close empty event"),200);assertThat(request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","No entrants"),409).toString()).contains("NOT_ENOUGH_PARTICIPANTS");
    }
    @Test void autoAndHybridReturnFinalApprovalWithoutPendingAndPreserveCodingWindow()throws Exception{
        for(String policy:List.of("AUTO_APPROVE","HYBRID")){
            String base=cup(policy,false,true);var flow=request(base+"/participant-registration","POST",signup(),200);
            assertThat(flow.path("registrationStatus").asText()).isEqualTo("APPROVED");assertThat(flow.path("nextAction").asText()).isEqualTo("WAIT_FOR_CODING");assertThat(flow.path("canModifyWorkspace").asBoolean()).isFalse();
            request(base+"/workspace","POST",Map.of("language","CPP"),409);request(base+"/participant-registration","POST",signup(),409);
            applicant();request(base+"/participant-registration","POST",signup(),200);loginOwner();clock.at(START.plusSeconds(60));request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Finalize verified roster"),200);clock.at(START.plusSeconds(120));assertThat(request(base+"/participant-flow","GET",null,200).path("nextAction").asText()).isEqualTo("OPEN_CODE_WORKSPACE");clock.at(START.plusSeconds(30));
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
