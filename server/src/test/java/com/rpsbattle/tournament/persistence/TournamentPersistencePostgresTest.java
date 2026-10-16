package com.rpsbattle.tournament.persistence;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.BotUploadService;
import com.rpsbattle.history.*;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.RankingService;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"rps.bots.enabled=false","rps.tournament.flush-millis=60000"})
@Testcontainers(disabledWithoutDocker=true)
class TournamentPersistencePostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine").withLabel("rps.stage","stage4-group6-test");
    @TempDir static Path root;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");registry.add("rps.bots.directory",root::toString);
    }
    @Autowired TournamentRepository repository;
    @Autowired UserRepository users;
    @Autowired OwnedBotService bots;
    @Autowired BotUploadService artifacts;
    @Autowired MatchPersistenceCoordinator historyCoordinator;
    @Autowired MatchHistoryRepository history;
    @Autowired UserStatsService stats;
    @Autowired RankingService rankings;
    @Autowired TournamentService tournaments;
    @Autowired DataSource database;
    User user() {var now=Instant.now();var id=UserId.random();var name="cup_"+id.value().toString().substring(0,8);return users.save(new User(id,name,name+"@example.com","unused","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null));}
    TournamentMetadata.Participant participant(User user,String name) {
        var bot=bots.upload(AuthService.sessionAuthentication(user),name,new MockMultipartFile("file","main.py","text/plain","print(1)".getBytes()));
        return new TournamentMetadata.Participant(UUID.randomUUID(),bot.id(),user.id().value(),name,42L);
    }
    TournamentMetadata draft(User owner,List<TournamentMetadata.Participant> participants) {return new TournamentMetadata(UUID.randomUUID(),owner.id().value(),"Persistent Cup",TournamentFormat.ROUND_ROBIN,TournamentStatus.DRAFT,1,Instant.parse("2026-10-13T00:00:00Z"),null,null,participants,List.of());}
    TournamentRegistry restored() {var registry=new TournamentRegistry();new TournamentPersistenceCoordinator(repository,registry,tournaments,historyCoordinator,artifacts).restore();return registry;}
    @Test void registrationCheckInAndReadyWithoutLiveScheduleKeepPhaseAndTimestampOnRestart() {
        var owner=user();var created=Instant.parse("2026-10-14T00:00:00Z");var changed=created.plusSeconds(60);
        for(var status:List.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY)) {
            var cup=new TournamentMetadata(UUID.randomUUID(),owner.id().value(),"Lifecycle Cup",TournamentFormat.ROUND_ROBIN,status,1,created,null,null,List.of(),List.of(),changed);
            repository.save(cup);var loaded=restored().findById(cup.id().toString()).orElseThrow();
            assertThat(loaded.status).isEqualTo(status);assertThat(loaded.statusChangedAt).isEqualTo(changed);assertThat(loaded.startedAt).isNull();
            assertThat(tournaments.view(loaded).get("statusChangedAt")).isEqualTo(changed);
        }
    }
    @Test void draftOwnershipRosterAndPrivateCapabilitiesSurviveRestoreWithoutBeingStoredInDb() throws Exception {
        var owner=user();var p=participant(owner,"Persistent bot");var cup=draft(owner,List.of(p));repository.save(cup);
        var registry=restored();var loaded=registry.findById(cup.id().toString()).orElseThrow();
        assertThat(loaded.ownerUserId).isEqualTo(owner.id().value());assertThat(loaded.status).isEqualTo(TournamentStatus.DRAFT);assertThat(loaded.participants).hasSize(1);
        assertThat(loaded.participants.getFirst().ownerUserId()).isEqualTo(owner.id().value());assertThat(loaded.participants.getFirst().seed()).isEqualTo(42L);
        assertThat(loaded.participants.getFirst().botToken()).isEqualTo(artifacts.stored(p.botId().toString()).token());
        assertThat(tournaments.view(loaded).toString()).doesNotContain(loaded.participants.getFirst().botToken(),"sourcePath","ownerToken");
        try(var c=database.getConnection();var sql=c.createStatement();var rows=sql.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name IN ('tournaments','tournament_participants','tournament_matches')")) {while(rows.next()) assertThat(rows.getString(1)).doesNotContain("token","password");}
    }
    @Test void registrationDecisionsCheckInSettingsAndTimestampsSurviveRestoreWithoutScheduling() {
        var owner=user();var p=participant(owner,"Checked");var q=participant(owner,"Pending");var time=Instant.parse("2026-10-14T02:00:00Z");var cupId=UUID.randomUUID();
        var checked=new TournamentRegistration(p.id(),cupId,owner.id().value(),p.botId(),p.botId(),p.displayName(),TournamentRegistration.Status.CHECKED_IN,time,time.plusSeconds(1),time.plusSeconds(2));
        var pending=new TournamentRegistration(q.id(),cupId,owner.id().value(),q.botId(),q.botId(),q.displayName(),TournamentRegistration.Status.PENDING,time,null,null);
        var cup=new TournamentMetadata(cupId,owner.id().value(),"Check-in restore",TournamentFormat.ROUND_ROBIN,TournamentStatus.CHECK_IN,5,time,null,null,List.of(p),List.of(),time,true,4,List.of(checked,pending));
        repository.save(cup);repository.save(cup);var loaded=restored().findById(cupId.toString()).orElseThrow();
        assertThat(loaded.status).isEqualTo(TournamentStatus.CHECK_IN);assertThat(loaded.checkInRequired).isTrue();assertThat(loaded.maxParticipants).isEqualTo(4);
        assertThat(loaded.registrations).containsExactlyInAnyOrder(checked,pending);assertThat(loaded.participants).hasSize(1);assertThat(loaded.matches).isEmpty();
        assertThat(loaded.registrations.stream().filter(r->r.status()==TournamentRegistration.Status.PENDING).findFirst().orElseThrow().approvedAt()).isNull();
    }
    @Test void botHashesAndManualSeedOrderSurviveDatabaseRestoreAndSeedSwap() {
        var owner=user();var a=participant(owner,"Alpha");var b=participant(owner,"Beta");var id=UUID.randomUUID();var time=Instant.parse("2026-10-14T01:00:00Z");
        var hashA=artifacts.freeze(a.botId().toString(),null);var hashB=artifacts.freeze(b.botId().toString(),null);
        var p=new TournamentMetadata.Participant(a.id(),a.botId(),a.ownerUserId(),a.displayName(),a.seed(),hashA,1,1);var q=new TournamentMetadata.Participant(b.id(),b.botId(),b.ownerUserId(),b.displayName(),b.seed(),hashB,2,2);
        var registration=new TournamentRegistration(p.id(),id,owner.id().value(),p.botId(),p.botId(),p.displayName(),TournamentRegistration.Status.APPROVED,time,time,null).withLock(hashA,time);
        repository.save(new TournamentMetadata(id,owner.id().value(),"Locked restore",TournamentFormat.ROUND_ROBIN,TournamentStatus.READY,1,time,null,null,List.of(p,q),List.of(),time,false,10,List.of(registration),SeedingStrategy.MANUAL,Long.MAX_VALUE));
        p=new TournamentMetadata.Participant(a.id(),a.botId(),a.ownerUserId(),a.displayName(),a.seed(),hashA,2,2);q=new TournamentMetadata.Participant(b.id(),b.botId(),b.ownerUserId(),b.displayName(),b.seed(),hashB,1,1);
        repository.save(new TournamentMetadata(id,owner.id().value(),"Locked restore",TournamentFormat.ROUND_ROBIN,TournamentStatus.READY,2,time,null,null,List.of(p,q),List.of(),time,false,10,List.of(registration),SeedingStrategy.MANUAL,Long.MAX_VALUE));
        var loaded=restored().findById(id.toString()).orElseThrow();assertThat(loaded.seedingStrategy).isEqualTo(SeedingStrategy.MANUAL);assertThat(loaded.seedingSeed).isEqualTo(Long.MAX_VALUE);
        assertThat(loaded.participants).extracting(TournamentParticipant::participantId).containsExactly(b.id().toString(),a.id().toString());assertThat(loaded.participants.getFirst().artifactHash()).isEqualTo(hashB);assertThat(loaded.registrations).containsExactly(registration);artifacts.verifyFrozen(a.botId().toString(),hashA);
    }
    @Test void finishedScheduleRankingAndLinkedAccountHistorySurviveRestoreAndDuplicateWrites() {
        var a=user();var b=user();var p=participant(a,"A");var q=participant(b,"B");var start=Instant.parse("2026-10-13T00:00:00Z");
        var archive=new MatchMetadata(UUID.randomUUID().toString(),"ROOM", "HUMAN_VS_HUMAN","FINISHED",a.id().value(),b.id().value(),a.id().value(),"P1","WIN",7,start,start.plusSeconds(1),1000L,"classic-online-v1",start,List.of(new MatchMetadata.Participant("P1",a.id().value(),p.botId(),MatchMetadata.ControllerType.UPLOADED_BOT),new MatchMetadata.Participant("P2",b.id().value(),q.botId(),MatchMetadata.ControllerType.UPLOADED_BOT)));
        var entry=new TournamentMetadata.Entry(UUID.randomUUID(),p.id(),q.id(),archive.id(),archive.roomId(),TournamentMatch.Status.FINISHED,p.id(),"WIN",null,12,3,start,start.plusSeconds(1),archive);
        var cup=new TournamentMetadata(UUID.randomUUID(),a.id().value(),"Finished Cup",TournamentFormat.ROUND_ROBIN,TournamentStatus.FINISHED,4,start,start,start.plusSeconds(1),List.of(p,q),List.of(entry));
        repository.save(cup);repository.save(cup);var restored=restored().findById(cup.id().toString()).orElseThrow();
        assertThat(restored.status).isEqualTo(TournamentStatus.FINISHED);assertThat(restored.matches).hasSize(1);assertThat(restored.matches.getFirst().historyMatchId).isEqualTo(archive.id());assertThat(restored.matches.getFirst().onlineMatchId).isEqualTo("ROOM");
        var ranking=rankings.rank(restored);assertThat(ranking.getFirst().participantId()).isEqualTo(p.id().toString());assertThat(ranking.getFirst().points()).isEqualTo(3);
        assertThat(history.find(archive.id())).isPresent();assertThat(stats.account(a.id().value()).wins()).isEqualTo(1);assertThat(stats.account(b.id().value()).losses()).isEqualTo(1);
    }
    @Test void restartingLiveTournamentMarksRecoveryAndNeverReplaysAlreadyFinishedResults() {
        var owner=user();var p=participant(owner,"First");var q=participant(owner,"Second");var time=Instant.parse("2026-10-13T00:00:00Z");
        var entry=new TournamentMetadata.Entry(UUID.randomUUID(),p.id(),q.id(),null,null,TournamentMatch.Status.RUNNING,null,null,null,0,0,time,null,null);
        var cup=new TournamentMetadata(UUID.randomUUID(),owner.id().value(),"Interrupted",TournamentFormat.ROUND_ROBIN,TournamentStatus.RUNNING,3,time,time,null,List.of(p,q),List.of(entry));repository.save(cup);
        var recovered=restored().findById(cup.id().toString()).orElseThrow();assertThat(recovered.status).isEqualTo(TournamentStatus.RECOVERY_REQUIRED);assertThat(recovered.matches.getFirst().status).isEqualTo(TournamentMatch.Status.FAILED);assertThat(recovered.matches.getFirst().failure).isEqualTo("SERVER_RESTART");
        var again=restored().findById(cup.id().toString()).orElseThrow();assertThat(again.revision).isEqualTo(recovered.revision);assertThat(stats.account(owner.id().value()).matchesPlayed()).isZero();
    }
    @Test void crossTournamentForeignKeyFailureRollsBackTournamentAndLinkedMatchInOneTransaction() {
        var a=user();var b=user();var foreign=participant(a,"Foreign");repository.save(draft(a,List.of(foreign)));var p=participant(a,"A");var q=participant(b,"B");var time=Instant.parse("2026-10-13T00:00:00Z");
        var archive=new MatchMetadata(UUID.randomUUID().toString(),"ROLLBACK","HUMAN_VS_HUMAN","FINISHED",a.id().value(),b.id().value(),b.id().value(),"P2","WIN",3,time,time,0L,"classic-online-v1",time,List.of());
        var entry=new TournamentMetadata.Entry(UUID.randomUUID(),foreign.id(),q.id(),archive.id(),archive.roomId(),TournamentMatch.Status.FINISHED,q.id(),"WIN",null,0,18,time,time,archive);
        var cup=new TournamentMetadata(UUID.randomUUID(),a.id().value(),"Rollback",TournamentFormat.ROUND_ROBIN,TournamentStatus.FINISHED,2,time,time,time,List.of(p,q),List.of(entry));
        assertThatThrownBy(()->repository.save(cup)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(repository.all()).noneMatch(t->t.id().equals(cup.id()));assertThat(history.find(archive.id())).isEmpty();assertThat(stats.account(b.id().value()).matchesPlayed()).isZero();
    }
    @Test void knockoutTreeRoundRestAndDecisionTimesSurviveRepeatedDatabaseRestore() {
        var owner=user();var t=new Tournament(UUID.randomUUID().toString(),"Knockout restore",TournamentFormat.SINGLE_ELIMINATION,Instant.parse("2026-10-14T03:00:00Z"));t.ownerUserId=owner.id().value();t.maxParticipants=32;t.maxConcurrentMatches=1;
        for(int i=0;i<3;i++){var p=participant(owner,"Bracket "+i);t.participants.add(new TournamentParticipant(p.id().toString(),p.botId().toString(),"unused",p.displayName(),p.seed(),p.ownerUserId(),null,i+1,null));}
        t.matches.addAll(new com.rpsbattle.tournament.scheduler.SingleEliminationScheduler().schedule(t));
        for(int i=0;i<3;i++) {for(var m:t.matches){m.status=TournamentMatch.Status.FINISHED;m.winnerParticipantId=m.participantA.participantId();m.result="WIN";m.remainingA=9;m.remainingB=0;m.moveCountA=2;m.moveNanosA=40000000;}
            t.matches.addAll(com.rpsbattle.tournament.scheduler.SingleEliminationScheduler.advance(t));}
        t.status=TournamentStatus.FINISHED;t.finishedAt=t.createdAt.plusSeconds(5);t.tieBreakOrder=List.of(com.rpsbattle.tournament.ranking.TieBreak.POINTS,com.rpsbattle.tournament.ranking.TieBreak.AVERAGE_MOVE_TIME);
        var coordinator=new TournamentPersistenceCoordinator(repository,new TournamentRegistry(),tournaments,historyCoordinator,artifacts);var snapshot=coordinator.snapshot(t);repository.save(snapshot);repository.save(snapshot);
        for(int i=0;i<2;i++) {var loaded=restored().findById(t.id).orElseThrow();assertThat(loaded.bracket).isEqualTo(t.bracket);assertThat(loaded.rounds).isEqualTo(t.rounds);assertThat(loaded.maxConcurrentMatches).isEqualTo(1);assertThat(loaded.matches).hasSize(2);assertThat(loaded.tieBreakOrder).isEqualTo(t.tieBreakOrder);
            assertThat(loaded.matches).allMatch(m->m.scheduledAt!=null&&m.moveCountA==2&&m.moveNanosA==40000000);assertThat(rankings.rank(loaded)).isEqualTo(rankings.rank(t));}
    }
    @Test void administrativeStateAndAppendOnlyAuditSurviveRestoreAndRejectConflictingRevision() throws Exception {
        var owner=user();var p=participant(owner,"Audit A");var q=participant(owner,"Audit B");var t=new Tournament(UUID.randomUUID().toString(),"Audit",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=owner.id().value();t.status=TournamentStatus.FINISHED;
        for(var value:List.of(p,q))t.participants.add(new TournamentParticipant(value.id().toString(),value.botId().toString(),"unused",value.displayName(),value.seed(),value.ownerUserId()));
        t.operations=t.operations.rounds(false,2).participant(p.id().toString(),ParticipantStatus.DISQUALIFIED).rules(Map.of("ruleSet","classic-online-v1","turnSeconds",30L));
        TournamentAudit.append(t,"PARTICIPANT_DISQUALIFIED",owner.id().value(),p.id().toString(),"reason","ACTIVE","DISQUALIFIED");t.revision=1;
        var coordinator=new TournamentPersistenceCoordinator(repository,new TournamentRegistry(),tournaments,historyCoordinator,artifacts);var first=coordinator.snapshot(t);repository.save(first);repository.save(first);
        TournamentAudit.append(t,"RESULT_OVERRIDDEN",owner.id().value(),"match","reviewed","DRAW","WIN");
        assertThatThrownBy(()->repository.save(coordinator.snapshot(t))).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        t.revision++;repository.save(coordinator.snapshot(t));var restored=restored().findById(t.id).orElseThrow();assertThat(restored.operations.audit()).hasSize(2);assertThat(restored.operations.participants()).containsEntry(p.id().toString(),ParticipantStatus.DISQUALIFIED);assertThat(restored.operations.autoAdvanceRounds()).isFalse();
        t.operations=TournamentOperations.defaults();t.revision++;assertThatThrownBy(()->repository.save(coordinator.snapshot(t))).isInstanceOf(RuntimeException.class);assertThat(restored().findById(t.id).orElseThrow().operations.audit()).hasSize(2);
        try(var c=database.getConnection();var sql=c.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM tournament_audit_events WHERE tournament_id='"+t.id+"'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}
    }
    @Test void bo3PersistsEveryGameWithoutCountingGameOneAsTheSeriesWinner() throws Exception {
        var owner=user();var p=participant(owner,"Series A");var q=participant(owner,"Series B");var t=new Tournament(UUID.randomUUID().toString(),"BO3 storage",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=owner.id().value();t.bestOf=3;t.status=TournamentStatus.RUNNING;
        for(var value:List.of(p,q))t.participants.add(new TournamentParticipant(value.id().toString(),value.botId().toString(),"unused",value.displayName(),value.seed(),value.ownerUserId()));
        var root=new TournamentMatch(UUID.randomUUID().toString(),t.id,t.participants.getFirst(),t.participants.getLast());t.matches.add(root);
        for(int i=0;i<3;i++){var game=t.matches.getLast();game.status=TournamentMatch.Status.FINISHED;game.winnerParticipantId=i==0?root.participantB.participantId():root.participantA.participantId();game.result="WIN";game.finishedAt=Instant.now();if(i<2)assertThat(TournamentSeries.advance(t)).isTrue();}
        t.status=TournamentStatus.FINISHED;t.revision=1;var coordinator=new TournamentPersistenceCoordinator(repository,new TournamentRegistry(),tournaments,historyCoordinator,artifacts);repository.save(coordinator.snapshot(t));repository.save(coordinator.snapshot(t));
        var restored=restored().findById(t.id).orElseThrow();assertThat(restored.bestOf).isEqualTo(3);assertThat(restored.matches).hasSize(3);var restoredRoot=restored.matches.stream().filter(m->m.id.equals(root.id)).findFirst().orElseThrow();assertThat(restoredRoot.winnerParticipantId).isEqualTo(root.participantB.participantId());assertThat(TournamentSeries.decision(restored,restoredRoot).orElseThrow().winnerParticipantId).isEqualTo(root.participantA.participantId());assertThat(rankings.rank(restored).getFirst().played()).isEqualTo(1);assertThat(rankings.rank(restored).getFirst().points()).isEqualTo(3);
        try(var c=database.getConnection();var query=c.prepareStatement("select count(*),count(distinct game_number) from tournament_matches where tournament_id=? and series_id=?")){query.setObject(1,UUID.fromString(t.id));query.setObject(2,UUID.fromString(root.id));try(var rows=query.executeQuery()){rows.next();assertThat(rows.getInt(1)).isEqualTo(3);assertThat(rows.getInt(2)).isEqualTo(3);}}
    }
    @Test void existingPrivateDatabaseRowsAreNeverRestoredAsPublic() throws Exception {
        var owner=user();var id=UUID.randomUUID();
        try(var c=database.getConnection();var sql=c.prepareStatement("INSERT INTO tournaments(id,owner_user_id,name,format,status,visibility) VALUES (?,?,'Private','ROUND_ROBIN','DRAFT','PRIVATE')")) {sql.setObject(1,id);sql.setObject(2,owner.id().value());sql.executeUpdate();}
        assertThat(repository.all()).noneMatch(t->t.id().equals(id));assertThat(restored().findById(id.toString())).isEmpty();
    }
}
