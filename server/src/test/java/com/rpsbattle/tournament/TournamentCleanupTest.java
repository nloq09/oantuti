package com.rpsbattle.tournament;
import com.rpsbattle.tournament.cleanup.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.persistence.*;
import com.rpsbattle.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@TestPropertySource(properties="rps.tournament.cleanup.enabled=false")
class TournamentCleanupTest extends StageThreeWebFixture {
 @Autowired TournamentCleanupService cleanup;@Autowired TournamentCleanupStore store;@Autowired TournamentRegistry registry;@Autowired TournamentPersistenceCoordinator persistence;@Autowired TournamentRepository repository;@Autowired JdbcTemplate db;@Autowired OwnedTournamentService ownedTournaments;
 Tournament old()throws Exception{loginAccount();var t=new Tournament(UUID.randomUUID().toString(),"Cleanup test",TournamentFormat.ROUND_ROBIN,Instant.now().minus(Duration.ofDays(40)));t.ownerUserId=account.id().value();registry.register(t);persistence.persistNow(t);return t;}
 UUID key(Tournament t){return UUID.fromString(t.id);}
 void graceElapsed(Tournament t){db.update("update tournament_cleanup set candidate_at=? where tournament_id=?",Timestamp.from(Instant.now().minus(Duration.ofHours(73))),key(t));}
 @Test void twoPassTrashIsDurableAndOldSnapshotsCannotResurrectIt()throws Exception{
  var t=old();var before=persistence.snapshot(t);cleanup.process();assertThat(store.state(key(t)).candidateAt()).isNotNull();assertThat(registry.findById(t.id)).isPresent();cleanup.process();assertThat(t.archived).isFalse();graceElapsed(t);cleanup.process();assertThat(t.archived).isTrue();assertThat(registry.findById(t.id)).isEmpty();assertThat(db.queryForObject("select trashed_at from tournaments where id=?",Timestamp.class,key(t))).isNotNull();repository.save(before);persistence.flush();persistence.restore();assertThat(registry.findById(t.id)).isEmpty();request("/api/tournaments/"+t.id,"GET",null,404);
  assertThatThrownBy(()->ownedTournaments.managedMutation(AuthService.sessionAuthentication(account),t,t.revision,()->t.name.length())).hasMessage("TOURNAMENT_NOT_FOUND");
 }
 @Test void changedRevisionRestartsGraceInsteadOfDeleting()throws Exception{
  var t=old();cleanup.process();graceElapsed(t);t.revision++;persistence.persistNow(t);cleanup.process();assertThat(t.archived).isFalse();assertThat(store.state(key(t)).revision()).isEqualTo(t.revision);assertThat(store.state(key(t)).candidateAt()).isAfter(Instant.now().minusSeconds(30));
 }
 @Test void dbWorkspaceAndApplicationProtectDataAbsentFromInMemoryRoster()throws Exception{
  var t=old();db.update("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,revision,status,created_at,updated_at) values(?,?,?,'PYTHON','',0,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),key(t),account.id().value());cleanup.process();assertThat(store.state(key(t))).isNull();assertThat(t.archived).isFalse();
  var other=old();db.update("insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,status,registered_at,rules_accepted_at) values(?,?,?,'Player','PYTHON','CANCELLED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),key(other),account.id().value());cleanup.process();assertThat(store.state(key(other))).isNull();
 }
 @Test void newWorkspaceDuringGraceCancelsCandidateInsteadOfTrashing()throws Exception{
  var t=old();cleanup.process();graceElapsed(t);db.update("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,revision,status,created_at,updated_at) values(?,?,?,'PYTHON','print(1)',0,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),key(t),account.id().value());cleanup.process();assertThat(t.archived).isFalse();assertThat(store.state(key(t)).candidateAt()).isNull();assertThat(t.operations.audit()).anyMatch(e->e.type().equals("CLEANUP_CLEARED"));
 }
 @Test void ownerCanRetainButCannotRestoreOrSeeAdminCleanup()throws Exception{
  var t=old();cleanup.process();request("/api/admin/tournament-cleanup","GET",null,403);request("/api/admin/tournament-cleanup/"+t.id+"/restore","POST",Map.of("reason","Keep local fixture"),403);
  request("/api/tournaments/"+t.id+"/keep","POST",Map.of("revision",t.revision+99,"reason","Still using"),409);request("/api/tournaments/"+t.id+"/keep","POST",Map.of("revision",t.revision,"reason","Still using"),200);assertThat(store.state(key(t)).candidateAt()).isNull();assertThat(store.state(key(t)).protectedUntil()).isAfter(Instant.now());cleanup.process();assertThat(t.archived).isFalse();
  var stranger=registration.register("stranger_"+UUID.randomUUID().toString().substring(0,8),UUID.randomUUID()+"@example.com","Tournament Test Password","Stranger");request("/api/auth/login","POST",Map.of("username",stranger.username(),"password","Tournament Test Password"),200);request("/api/tournaments/"+t.id+"/keep","POST",Map.of("revision",t.revision,"reason","Other owner"),403);
 }
 @Test void administratorCanRestoreAndRestoredDraftHasProtection()throws Exception{
  var t=old();cleanup.process();graceElapsed(t);cleanup.process();db.update("update users set role='ADMIN' where id=?",account.id().value());request("/api/auth/login","POST",Map.of("username",account.username(),"password","Tournament Test Password"),200);
  request("/api/admin/tournament-cleanup/"+t.id+"/restore","POST",Map.of("reason","Verified useful tournament"),200);assertThat(registry.findById(t.id)).isPresent();assertThat(store.state(key(t)).protectedUntil()).isAfter(Instant.now());cleanup.process();assertThat(registry.findById(t.id)).isPresent();var restored=registry.findById(t.id).orElseThrow();assertThat(restored.operations.audit()).anyMatch(e->e.type().equals("TOURNAMENT_RESTORED"));
 }
}
