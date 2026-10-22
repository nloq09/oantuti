package com.rpsbattle.tournament.registration;
import com.rpsbattle.tournament.coding.SubmissionDashboardStore;
import com.rpsbattle.tournament.timeline.TimelineRegistrationStore;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"rps.bots.enabled=false","rps.bots.directory=${java.io.tmpdir}/rps-submission-dashboard-${random.uuid}"})
@Testcontainers
class SubmissionDashboardPostgresTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");}
 @Autowired SubmissionDashboardStore dashboard;@Autowired TimelineRegistrationStore applications;@Autowired JdbcTemplate db;
 static final Instant NOW=Instant.parse("2026-10-21T12:00:00Z");
 UUID user(){UUID id=UUID.randomUUID();String name="sub_"+id.toString().substring(0,8);db.update("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",id,name,name+"@example.com","fixture",name);return id;}
 UUID cup(){UUID id=UUID.randomUUID();db.update("insert into tournaments(id,owner_user_id,name,format,status,max_participants) values(?,?,'Submission dashboard','ROUND_ROBIN','REGISTRATION',10)",id,user());return id;}
 void approve(UUID t,UUID u){applications.register(t,u,"Player","CPP",null,10,NOW,new EligibilityDecision(EligibilityDecision.Decision.APPROVE,List.of()));}
 UUID workspace(UUID t,UUID u,String lang){UUID id=UUID.randomUUID();db.update("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,created_at,updated_at) values(?,?,?,?,'PRIVATE SOURCE',?,?)",id,t,u,lang,java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));return id;}
 UUID ready(UUID w,int number){UUID id=UUID.randomUUID();db.update("insert into tournament_submission_versions(id,workspace_id,version_number,source_code,source_hash,status,build_log,public_passed,sample_passed,created_at,updated_at) values(?,?,?,'PRIVATE SOURCE',?,'READY','SECRET LOG',true,true,?,?)",id,w,number,"a".repeat(64),java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));return id;}
 Map<String,Object> read(UUID t,String filter,String search,int page,int size){return dashboard.read(t.toString(),NOW,page,size,filter,search);}
 @SuppressWarnings("unchecked") Map<String,Object> summary(Map<String,Object> data){return (Map<String,Object>)data.get("summary");}
 @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> data){return (List<Map<String,Object>>)data.get("items");}
 @Test void approvedPeopleWithoutWorkspaceAreIncludedAndPrivatePayloadIsAbsent(){UUID t=cup(),u=user();approve(t,u);var data=read(t,"ALL","",0,25);assertThat(((Number)summary(data).get("approved")).longValue()).isEqualTo(1);assertThat(items(data)).hasSize(1);assertThat(items(data).getFirst()).containsEntry("workspace_state","NO_WORKSPACE");assertThat(data.toString()).doesNotContain("source_code","build_log","email","password");}
 @Test void multipleLanguagesAndImmutableVersionsCountOnePersonAndFiltersRunBeforePaging(){UUID t=cup(),u=user();approve(t,u);ready(workspace(t,u,"CPP"),1);UUID py=workspace(t,u,"PYTHON");ready(py,1);ready(py,2);UUID empty=user();approve(t,empty);var data=read(t,"READY","",0,1);assertThat(items(data)).hasSize(1);assertThat(((Number)data.get("total")).longValue()).isEqualTo(1);assertThat(((Number)summary(data).get("approved")).longValue()).isEqualTo(2);assertThat(((Number)summary(data).get("workspace_users")).longValue()).isEqualTo(1);assertThat(((Number)summary(data).get("ready")).longValue()).isEqualTo(1);assertThat(items(read(t,"NO_WORKSPACE","",0,25))).hasSize(1);assertThat(items(read(t,"ALL","",1,1))).hasSize(1);}
 @Test void filteredEmptyDoesNotEraseGlobalTotalsAndSearchIsLiteral(){UUID t=cup(),u=user();approve(t,u);db.update("update users set display_name='Alpha %_ Player' where id=?",u);assertThat(items(read(t,"ALL","%_",0,25))).hasSize(1);var empty=read(t,"ALL","no such player",0,25);assertThat(items(empty)).isEmpty();assertThat(((Number)summary(empty).get("approved")).longValue()).isEqualTo(1);assertThat(items(read(t,"ALL","' OR 1=1 --",0,25))).isEmpty();}
 @Test void onlyCurrentCutoffFreezeIsUsedAndPendingIsNotClaimedLocked(){UUID t=cup(),u=user();approve(t,u);db.update("insert into tournament_coding_freezes(id,tournament_id,user_id,coding_close_at,decision,reason,frozen_at) values(?,?,?,?,'MISSING','NO_READY_VERSION_BEFORE_DEADLINE',?)",UUID.randomUUID(),t,u,java.sql.Timestamp.from(NOW.minusSeconds(60)),java.sql.Timestamp.from(NOW));assertThat(items(read(t,"ALL","",0,25)).getFirst()).containsEntry("freeze_state","PENDING");db.update("insert into tournament_coding_freezes(id,tournament_id,user_id,coding_close_at,decision,reason,frozen_at) values(?,?,?,?,'MISSING','NO_READY_VERSION_BEFORE_DEADLINE',?)",UUID.randomUUID(),t,u,java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));assertThat(items(read(t,"NO_VALID_SUBMISSION","",0,25)).getFirst()).containsEntry("freeze_state","MISSING");assertThat(items(read(t,"LOCKED","",0,25))).isEmpty();}
 @Test void rejectedAndPendingApplicantsNeverInflateApprovedCounts(){UUID t=cup();approve(t,user());applications.register(t,user(),"Pending","CPP",null,10,NOW);assertThat(items(read(t,"ALL","",0,25))).hasSize(1);}
 @Test void invalidPagingAndFiltersAreRejected(){UUID t=cup();assertThatThrownBy(()->read(t,"ALL","",-1,25)).hasMessage("INVALID_SUBMISSION_FILTER");assertThatThrownBy(()->read(t,"INVALID","",0,25)).hasMessage("INVALID_SUBMISSION_FILTER");assertThatThrownBy(()->read(t,"ALL","",0,101)).hasMessage("INVALID_SUBMISSION_FILTER");}
 @Test void validFrozenVersionIsCountedAndLocalizedSeparatelyFromUnfrozenReadyVersions(){UUID t=cup(),u=user();approve(t,u);UUID version=ready(workspace(t,u,"CPP"),1);db.update("insert into tournament_coding_freezes(id,tournament_id,user_id,coding_close_at,decision,version_id,version_number,language,source_hash,artifact_hash,submitted_at,frozen_at) values(?,?,?,?,'EXPLICIT_SUBMIT',?,1,'CPP',?,?,?,?)",UUID.randomUUID(),t,u,java.sql.Timestamp.from(NOW),version,"a".repeat(64),"b".repeat(64),java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));var data=read(t,"LOCKED","",0,25);assertThat(items(data)).hasSize(1);assertThat(items(data).getFirst()).containsEntry("freeze_state","LOCKED").containsEntry("frozen_version",1);assertThat(((Number)summary(data).get("frozen")).longValue()).isEqualTo(1);assertThat(items(read(t,"NO_VALID_SUBMISSION","",0,25))).isEmpty();}
}
