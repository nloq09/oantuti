package com.rpsbattle.admin;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentAuditReadTest extends AdminAuditPresentationTest {
    private final List<String> registeredCups=new ArrayList<>();
    @org.junit.jupiter.api.AfterEach void clearRegistry(){registeredCups.forEach(registry::remove);}
    @Autowired TournamentAuditReadService scoped;
    @Autowired TournamentRegistry registry;
    @Autowired TournamentService tournaments;
    UUID cup(User owner){UUID id=tournament(owner,"Scoped audit "+UUID.randomUUID());var t=new Tournament(id.toString(),"Scoped audit",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=owner.id().value();registry.register(t);registeredCups.add(t.id);return id;}
    UUID validation(UUID cup,User player){
        var workspace=UUID.randomUUID();var version=UUID.randomUUID();var freeze=UUID.randomUUID();var id=UUID.randomUUID();var at=java.sql.Timestamp.from(Instant.now());var hash="a".repeat(64);
        db.update("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,created_at,updated_at) values(?,?,?,'PYTHON','private source',?,?)",workspace,cup,player.id().value(),at,at);
        db.update("insert into tournament_submission_versions(id,workspace_id,version_number,source_code,source_hash,status,build_log,created_at,updated_at) values(?,?,1,'private source',?,'READY','',?,?)",version,workspace,hash,at,at);
        db.update("insert into tournament_coding_freezes(id,tournament_id,user_id,coding_close_at,decision,version_id,version_number,language,source_hash,artifact_hash,submitted_at,frozen_at) values(?,?,?,?,'AUTO_SUBMITTED',?,1,'PYTHON',?,?,?,?)",freeze,cup,player.id().value(),at,version,hash,hash,at,at);
        db.update("insert into tournament_final_validations(id,freeze_id,tournament_id,user_id,version_id,artifact_hash,source_hash,status,failure_policy,steps_json,history_json,created_at,applied_at) values(?,?,?,?,?,?,?,'PASSED','DISQUALIFY','[]','[]',?,?)",id,freeze,cup,player.id().value(),version,hash,hash,at,at);return id;
    }
    @Test void scopedPermissionAndIsolationCoverOwnerAdminParticipantAndGuest(){
        var owner=user(UserRole.USER);var other=user(UserRole.USER);var admin=user(UserRole.ADMIN);var a=cup(owner);var b=cup(other);
        event(a,1,"TOURNAMENT_CREATED",owner.id().value(),a.toString(),null,null);event(b,1,"TOURNAMENT_CANCELLED",other.id().value(),b.toString(),null,null);
        assertThat(items(scoped.list(auth(owner),a.toString(),Map.of(),0,25))).hasSize(1);assertThat(items(scoped.list(auth(admin),a.toString(),Map.of(),0,25))).hasSize(1);
        assertThatThrownBy(()->scoped.list(auth(other),a.toString(),Map.of(),0,25)).isInstanceOf(com.rpsbattle.auth.AuthException.class);assertThatThrownBy(()->scoped.list(null,a.toString(),Map.of(),0,25)).isInstanceOf(com.rpsbattle.auth.AuthException.class);
        assertThat(items(scoped.list(auth(owner),a.toString(),Map.of("action","TOURNAMENT_CANCELLED"),0,25))).isEmpty();
    }
    @Test void validationPairsMergeBeforePagingButOriginalAuditRecordsRemain(){
        var owner=user(UserRole.USER);var player=user(UserRole.USER);var cup=cup(owner);var validation=validation(cup,player);
        for(int i=0;i<31;i++){event(cup,i*2+1,"TIMELINE_BOT_VALIDATION_PASSED",null,player.id().value().toString(),null,"PASSED");event(cup,i*2+2,"FINAL_VALIDATION_PASSED",null,validation.toString(),null,"PASSED");}
        clearInvocations(db);var page=scoped.list(auth(owner),cup.toString(),Map.of("category","VALIDATION","outcome","PASSED"),1,25);
        assertThat(page.get("total")).isEqualTo(31L);assertThat(items(page)).hasSize(6);assertThat(items(page)).allSatisfy(row->{assertThat(row.get("merged_id")).isNotNull();assertThat(row).containsEntry("bot_language","PYTHON").containsEntry("target_user",player.username()).containsEntry("bot_version",1);});
        long reads=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("query")&&i.getMethod().getParameterCount()==3&&i.getMethod().getParameterTypes()[1]==org.springframework.jdbc.core.PreparedStatementSetter.class&&i.getArguments()[0].toString().contains("from visible")).count();assertThat(reads).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from tournament_audit_events where tournament_id=?",Long.class,cup)).isEqualTo(62);
        var peer=items(page).getFirst().get("merged_id").toString();assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of("audit_id",peer,"action","TIMELINE_BOT_VALIDATION_PASSED"),0,25))).hasSize(1);
    }
    @Test void unrelatedValidationEventsNeverMergeByTimestampAlone(){
        var owner=user(UserRole.USER);var player=user(UserRole.USER);var other=user(UserRole.USER);var cup=cup(owner);var validation=validation(cup,player);
        event(cup,1,"TIMELINE_BOT_VALIDATION_PASSED",null,other.id().value().toString(),null,"PASSED");event(cup,2,"FINAL_VALIDATION_PASSED",null,validation.toString(),null,"PASSED");
        assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of(),0,25))).hasSize(2).allSatisfy(row->assertThat(row.get("merged_id")).isNull());
        db.update("update tournament_audit_events set target_id=?,occurred_at=? where tournament_id=? and sequence_number=1",player.id().value().toString(),java.sql.Timestamp.from(Instant.now().minusSeconds(30)),cup);
        assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of(),0,25))).hasSize(2);
    }
    @Test void scopedFiltersSeparateRemindersAndUseLiteralSearch(){
        var owner=user(UserRole.USER);var cup=cup(owner);event(cup,1,"TIMELINE_CODING_1_MIN_LEFT",null,cup.toString(),null,null);event(cup,2,"TOURNAMENT_CREATED",owner.id().value(),cup.toString(),null,null);
        assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of(),0,25))).hasSize(1);assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of("kind","ALL"),0,25))).hasSize(2);assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of("kind","ACTIVITY","category","CODING","actor_kind","SYSTEM"),0,25))).hasSize(1);
        assertThat(items(scoped.list(auth(owner),cup.toString(),Map.of("q","%_' or 1=1"),0,25))).isEmpty();
        for(var filters:List.of(Map.of("category","BAD"),Map.of("kind","BAD"),Map.of("actor_kind","BAD"),Map.of("from","bad-date")))assertThatThrownBy(()->scoped.list(auth(owner),cup.toString(),filters,0,25)).isInstanceOf(com.rpsbattle.auth.AuthException.class);
    }
    @Test void sensitiveDetailsAndLegacyProjectionAreRedactedWithoutMutatingDomain(){
        var owner=user(UserRole.USER);var cup=cup(owner);String raw="{\"status\":\"PENDING\",\"password\":\"private-password\",\"workerCredential\":\"private-worker\",\"signed\":\"https://example.invalid/file?X-Amz-Signature=private-signature&sig=private-sig\"}";
        event(cup,1,"TOURNAMENT_UPDATED",owner.id().value(),cup.toString(),raw,"APPROVED");var rows=items(scoped.list(auth(owner),cup.toString(),Map.of(),0,25));assertThat(rows.toString()).doesNotContain("private-");
        var t=tournaments.find(cup.toString());var original=new TournamentAuditEvent(UUID.randomUUID(),1,"TOURNAMENT_UPDATED",owner.id().value(),cup.toString(),null,raw,"APPROVED",Instant.now());t.operations=t.operations.append(original);
        assertThat(tournaments.view(t).get("audit").toString()).doesNotContain("private-");assertThat(original.beforeValue()).isEqualTo(raw);
    }
}
