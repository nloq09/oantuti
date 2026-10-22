package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.timeline.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimelineReviewApiTest extends StageThreeWebFixture {
 @MockitoBean Clock clock;
 @Autowired TimelineRegistrationService service;
 @Autowired com.rpsbattle.account.repository.UserRepository users;
 static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
 @BeforeEach void time(){doReturn(START.plusSeconds(30)).when(clock).instant();doReturn(ZoneOffset.UTC).when(clock).getZone();}
 String cup()throws Exception{loginAccount();var t=request("/api/tournaments","POST",Map.of("name","Review applications","botPolicy",Map.of("submissionMode","BOTH")),201);String base="/api/tournaments/"+t.path("id").asText();var timeline=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(180),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);request(base+"/timeline","PUT",Map.of("revision",t.path("revision").asLong(),"timeline",timeline),200);request(base+"/open-registration","POST",Map.of(),200);return base;}
 String apply(String base)throws Exception{return request(base+"/participant-registration","POST",Map.of("intendedLanguage","CPP","rulesAccepted",true),200).path("registration").path("id").asText();}
 long revision(String base)throws Exception{return request(base,"GET",null,200).path("revision").asLong();}
 void review(String base,String application,String decision,String reason,long revision,int status)throws Exception{request(base+"/participant-applications/"+application+"/review","POST",Map.of("decision",decision,"reason",reason,"revision",revision),status);}
 @Test void ownerCanApproveAfterRegistrationCloseAndWaitingScreenHasAuthoritativePhase()throws Exception{
  String base=cup(),application=apply(base);when(clock.instant()).thenReturn(START.plusSeconds(60));assertThat(request(base+"/participant-flow","GET",null,200).path("phase").asText()).isEqualTo("REGISTRATION_REVIEW");
  review(base,application,"APPROVED","Application accepted",revision(base),200);var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.path("registrationStatus").asText()).isEqualTo("APPROVED");assertThat(flow.path("phase").asText()).isEqualTo("WAITING_FOR_CODING");assertThat(flow.path("nextAction").asText()).isEqualTo("WAIT_FOR_CODING");assertThat(request(base,"GET",null,200).path("participants").size()).isZero();
  request(base+"/workspace","POST",Map.of("language","CPP"),409);when(clock.instant()).thenReturn(START.plusSeconds(120));assertThat(request(base+"/participant-flow","GET",null,200).path("nextAction").asText()).isEqualTo("OPEN_CODE_WORKSPACE");review(base,application,"REJECTED","Too late",revision(base),409);
 }
 @Test void rejectionAndWaitlistKeepPrivateReasonsAndAuditAndNeverAuthorizeCode()throws Exception{
  String base=cup(),application=apply(base);review(base,application,"WAITLISTED","Private waitlist reason",revision(base),200);assertThat(request(base+"/participant-flow","GET",null,200).path("registrationStatus").asText()).isEqualTo("WAITLISTED");review(base,application,"REJECTED","Private rejection reason",revision(base),200);
  var flow=request(base+"/participant-flow","GET",null,200);assertThat(flow.toString()).doesNotContain("Private rejection reason","Private waitlist reason","reviewReason","reasonCodes");assertThat(flow.path("nextAction").asText()).isEqualTo("REGISTRATION_REJECTED");
  for(String path:List.of("","/summary","/export"))assertThat(request(base+path,"GET",null,200).toString()).doesNotContain("Private rejection reason","Private waitlist reason");
  var tournament=tournaments.find(base.substring(base.lastIndexOf('/')+1));assertThat(tournament.operations.audit()).anyMatch(e->e.type().equals("PARTICIPANT_APPLICATION_REVIEWED")&&e.reason().equals("Private rejection reason")&&e.beforeValue().equals("WAITLISTED")&&e.afterValue().equals("REJECTED"));
  when(clock.instant()).thenReturn(START.plusSeconds(120));request(base+"/workspace","POST",Map.of("language","CPP"),403);
 }
 @Test void reviewRequiresReasonFreshRevisionMatchingTournamentAndValidTransition()throws Exception{
  String base=cup(),application=apply(base);long revision=revision(base);review(base,application,"APPROVED","",revision,400);review(base,application,"APPROVED","Accept",revision-1,409);review(base,application,"UNKNOWN","Invalid",revision,400);review(base,UUID.randomUUID().toString(),"APPROVED","Wrong row",revision,404);
  String other=cup();review(other,application,"APPROVED","Cross tournament",revision(other),404);request(base+"/participant-registration","DELETE",Map.of(),200);review(base,application,"APPROVED","Cancelled",revision(base),409);
 }
 @Test void ordinaryAccountCannotReadOrReviewOthersButAdministratorCan()throws Exception{
  String base=cup(),application=apply(base);String username="review_"+UUID.randomUUID().toString().substring(0,8);var user=registration.register(username,username+"@example.com","Review Test Password","Reviewer");request("/api/auth/login","POST",Map.of("username",username,"password","Review Test Password"),200);request(base+"/participant-applications","GET",null,403);review(base,application,"APPROVED","Forbidden",revision(base),403);assertThat(request(base+"/participant-flow","GET",null,200).path("registration").isNull()).isTrue();
  var admin=new com.rpsbattle.account.domain.User(user.id(),user.username(),user.email(),user.passwordHash(),user.displayName(),com.rpsbattle.account.domain.UserRole.ADMIN,user.status(),user.createdAt(),user.updatedAt(),user.lastLoginAt(),user.authVersion());users.save(admin);request("/api/auth/login","POST",Map.of("username",username,"password","Review Test Password"),200);review(base,application,"APPROVED","Admin approval",revision(base),200);assertThat(request(base+"/participant-applications?status=APPROVED","GET",null,200).path("items").size()).isEqualTo(1);request(base+"/participant-applications?page=-1","GET",null,400);request(base+"/participant-applications?status=FORGED","GET",null,400);
 }
}
