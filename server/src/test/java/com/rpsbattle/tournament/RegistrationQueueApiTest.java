package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.TournamentTimeline;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationQueueApiTest extends StageThreeWebFixture {
    @MockitoBean Clock clock;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate database;
    static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
    @BeforeEach void time(){doReturn(START.plusSeconds(30)).when(clock).instant();doReturn(ZoneOffset.UTC).when(clock).getZone();}
    String cup(int capacity)throws Exception{
        loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Registration queue","format","SWISS","maxParticipants",capacity,"botPolicy",Map.of("submissionMode","BOTH"),"registrationPolicy",Map.of("approvalPolicy","MANUAL_REVIEW","requireAffiliationVerification",false)),201);
        String base="/api/tournaments/"+created.path("id").asText();
        var timeline=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(180),START.plusSeconds(210),START.plusSeconds(240),START.plusSeconds(270),START.plusSeconds(300),START.plusSeconds(360),null);
        request(base+"/timeline","PUT",Map.of("revision",created.path("revision").asLong(),"timeline",timeline),200);request(base+"/open-registration","POST",Map.of(),200);return base;
    }
    long revision(String base)throws Exception{return request(base,"GET",null,200).path("revision").asLong();}
    Map<String,Object> signup(){return Map.of("rulesAccepted",true,"intendedLanguage","CPP","affiliation","UET");}
    Map<String,Object> entry(JsonNode value){return Map.of("id",value.path("id").asText(),"status",value.path("status").asText(),"registeredAt",value.path("registeredAt").asText());}
    Map<String,Object> bulk(String base,String decision,List<?> entries)throws Exception{return Map.of("revision",revision(base),"decision",decision,"reason","Verified applications","items",entries);}
    List<Map<String,Object>> seed(String base,int n){
        UUID tournament=UUID.fromString(base.substring(base.lastIndexOf('/')+1));var users=new ArrayList<Object[]>();var applications=new ArrayList<Object[]>();var result=new ArrayList<Map<String,Object>>();
        for(int i=0;i<n;i++){
            UUID user=UUID.randomUUID(),id=UUID.randomUUID();String username="queue_"+i+"_"+user.toString().substring(0,8);String status=i<100?"PENDING":"APPROVED";
            users.add(new Object[]{user,username,username+"@example.com","unused-test-hash","Player "+i});
            applications.add(new Object[]{id,tournament,user,"Player "+i,i%3==0?"JAVA":"CPP",i%2==0?"UET":"HUST",status,java.sql.Timestamp.from(START.plusSeconds(i)),java.sql.Timestamp.from(START),i<50?"AFFILIATION_NOT_VERIFIED":""});
            result.add(Map.of("id",id.toString(),"status",status.equals("PENDING")?"NEEDS_REVIEW":status,"registeredAt",START.plusSeconds(i).toString()));
        }
        database.batchUpdate("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",users);
        database.batchUpdate("insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,affiliation,status,registered_at,rules_accepted_at,eligibility_reasons) values(?,?,?,?,?,?,?,?,?,?)",applications);
        return result;
    }
    @Test void filtersRunBeforePaginationAndCountsAreGlobalToTheFilteredQuery()throws Exception{
        String base=cup(4000);seed(base,128);
        var first=request(base+"/participant-applications?size=100","GET",null,200);assertThat(first.path("total").asInt()).isEqualTo(128);assertThat(first.path("items").size()).isEqualTo(100);
        var second=request(base+"/participant-applications?size=100&page=1","GET",null,200);assertThat(second.path("items").size()).isEqualTo(28);
        assertThat(request(base+"/participant-applications?size=50&page=999","GET",null,200).path("page").asInt()).isEqualTo(2);
        var filtered=request(base+"/participant-applications?status=NEEDS_REVIEW&affiliation=uet&language=JAVA&reason=AFFILIATION_NOT_VERIFIED&from=2026-10-19T00:00:00Z&until=2026-10-19T00:00:50Z","GET",null,200);
        assertThat(filtered.path("total").asInt()).isEqualTo(9);assertThat(filtered.path("items").size()).isEqualTo(9);
        assertThat(request(base+"/participant-applications?reason=MANUAL_REVIEW_REQUIRED","GET",null,200).path("total").asInt()).isEqualTo(50);
        assertThat(request(base+"/participant-applications?search=queue_7_","GET",null,200).path("total").asInt()).isEqualTo(1);
        assertThat(request(base+"/participant-applications?search=Player%20127","GET",null,200).path("items").size()).isEqualTo(1);
        assertThat(request(base+"/participant-applications?search=%25","GET",null,200).path("total").asInt()).isZero();
        assertThat(request(base+"/participant-applications?from=2026-10-19T00:00:01Z&until=2026-10-19T00:00:02Z","GET",null,200).path("total").asInt()).isEqualTo(1);
        for(String query:List.of("size=4000","size=0","page=-1","language=RUST","reason=PRIVATE_NOTE","status=unknown","from=2026-10-20T00:00:00Z&until=2026-10-19T00:00:00Z","from=invalid"))request(base+"/participant-applications?"+query,"GET",null,400);
    }
    @Test void bulkReturnsPartialCapacityFailureAndAuditsEveryCommittedDecision()throws Exception{
        String base=cup(2);var entries=seed(base,3);var result=request(base+"/participant-applications/bulk-review","POST",bulk(base,"APPROVED",entries),200);
        assertThat(result.path("succeeded").asInt()).isEqualTo(2);assertThat(result.path("failed").asInt()).isEqualTo(1);assertThat(result.path("items").get(2).path("error").asText()).isEqualTo("CAPACITY_FULL");
        assertThat(request(base+"/registration-summary","GET",null,200).path("counts").path("approved").asInt()).isEqualTo(2);
        for(int i=0;i<2;i++){var detail=request(base+"/participant-applications/"+entries.get(i).get("id"),"GET",null,200);assertThat(detail.path("history").size()).isEqualTo(1);assertThat(detail.path("history").get(0).path("actor").asText()).isEqualTo(account.id().value().toString());assertThat(detail.path("history").get(0).path("reason").asText()).isEqualTo("Verified applications");}
    }
    @Test void staleForeignAndChangedRowsNeverPreventUnrelatedValidRowsFromCommitting()throws Exception{
        String foreign=cup(10);var foreignEntry=seed(foreign,1).getFirst();String base=cup(10);var entries=seed(base,3);
        var items=new ArrayList<Map<String,Object>>();items.add(entries.get(0));items.add(foreignEntry);var changed=new HashMap<>(entries.get(1));changed.put("status","APPROVED");items.add(changed);var rejoined=new HashMap<>(entries.get(2));rejoined.put("registeredAt",START.minusSeconds(1).toString());items.add(rejoined);
        var result=request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",items),200);assertThat(result.path("succeeded").asInt()).isEqualTo(1);assertThat(result.path("failed").asInt()).isEqualTo(3);assertThat(result.path("items").get(1).path("error").asText()).isEqualTo("APPLICATION_NOT_FOUND");assertThat(result.path("items").get(2).path("error").asText()).isEqualTo("APPLICATION_CHANGED");assertThat(result.path("items").get(3).path("error").asText()).isEqualTo("APPLICATION_CHANGED");
        assertThat(request(foreign+"/participant-applications/"+foreignEntry.get("id"),"GET",null,200).path("details").path("application").path("status").asText()).isEqualTo("NEEDS_REVIEW");
    }
    @Test void bulkRejectRequiresReasonFreshRevisionUniqueBoundedSelectionAndOpenReview()throws Exception{
        String base=cup(10);var entries=seed(base,2);var payload=new HashMap<>(bulk(base,"REJECTED",entries));payload.put("reason","");request(base+"/participant-applications/bulk-review","POST",payload,400);
        payload=new HashMap<>(bulk(base,"REJECTED",entries));payload.put("revision",revision(base)-1);request(base+"/participant-applications/bulk-review","POST",payload,409);
        request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",List.of(entries.getFirst(),entries.getFirst())),400);
        request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",List.of()),400);
        request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",Collections.nCopies(101,entries.getFirst())),400);
        assertThat(request(base+"/registration-summary","GET",null,200).path("counts").path("needsReview").asInt()).isEqualTo(2);
        doReturn(START.plusSeconds(120)).when(clock).instant();assertThat(request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",entries),409).toString()).contains("APPLICATION_REVIEW_CLOSED");
    }
    @Test void bulkAllowedAfterAdmissionClosesButCannotChangeLockedRoster()throws Exception{
        String base=cup(2);var entries=seed(base,2);request(base+"/registration/close","POST",Map.of("revision",revision(base),"reason","Close admission"),200);
        assertThat(request(base+"/participant-applications/bulk-review","POST",bulk(base,"APPROVED",entries),200).path("succeeded").asInt()).isEqualTo(2);
        request(base+"/roster/lock","POST",Map.of("revision",revision(base),"reason","Finalize roster"),200);
        request(base+"/participant-applications/bulk-review","POST",bulk(base,"REJECTED",entries),409);
    }
    @Test void bulkApprovalRevalidatesAccountEligibilityPerRow()throws Exception{
        String base=cup(10);var entries=seed(base,2);
        database.update("update users set status='DISABLED' where id=(select user_id from tournament_timeline_registrations where id=?)",UUID.fromString(entries.getFirst().get("id").toString()));
        var result=request(base+"/participant-applications/bulk-review","POST",bulk(base,"APPROVED",entries),200);
        assertThat(result.path("succeeded").asInt()).isEqualTo(1);assertThat(result.path("items").get(0).path("error").asText()).isEqualTo("ACCOUNT_NOT_ELIGIBLE");
    }
    @Test void detailAndBulkAreManagerOnlyAndNeverExposeSecretsOrOtherApplicationHistory()throws Exception{
        String base=cup(10);var entries=seed(base,2);var payload=bulk(base,"REJECTED",List.of(entries.getFirst()));request(base+"/participant-applications/bulk-review","POST",payload,200);
        var detail=request(base+"/participant-applications/"+entries.get(1).get("id"),"GET",null,200);assertThat(detail.path("history").size()).isZero();assertThat(detail.toString()).doesNotContain("password","@example.com","unused-test-hash");
        String other="queue_other_"+UUID.randomUUID().toString().substring(0,8);registration.register(other,other+"@example.com","Queue Test Password",other);request("/api/auth/login","POST",Map.of("username",other,"password","Queue Test Password"),200);
        request(base+"/participant-applications/"+entries.getFirst().get("id"),"GET",null,403);request(base+"/participant-applications/bulk-review","POST",payload,403);request(base+"/participant-applications?search=Player","GET",null,403);
    }
}
