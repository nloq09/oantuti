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
    @Test void draftOwnershipRosterAndPrivateCapabilitiesSurviveRestoreWithoutBeingStoredInDb() throws Exception {
        var owner=user();var p=participant(owner,"Persistent bot");var cup=draft(owner,List.of(p));repository.save(cup);
        var registry=restored();var loaded=registry.findById(cup.id().toString()).orElseThrow();
        assertThat(loaded.ownerUserId).isEqualTo(owner.id().value());assertThat(loaded.status).isEqualTo(TournamentStatus.DRAFT);assertThat(loaded.participants).hasSize(1);
        assertThat(loaded.participants.getFirst().ownerUserId()).isEqualTo(owner.id().value());assertThat(loaded.participants.getFirst().seed()).isEqualTo(42L);
        assertThat(loaded.participants.getFirst().botToken()).isEqualTo(artifacts.stored(p.botId().toString()).token());
        assertThat(tournaments.view(loaded).toString()).doesNotContain(loaded.participants.getFirst().botToken(),"sourcePath","ownerToken");
        try(var c=database.getConnection();var sql=c.createStatement();var rows=sql.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name IN ('tournaments','tournament_participants','tournament_matches')")) {while(rows.next()) assertThat(rows.getString(1)).doesNotContain("token","password");}
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
    @Test void existingPrivateDatabaseRowsAreNeverRestoredAsPublic() throws Exception {
        var owner=user();var id=UUID.randomUUID();
        try(var c=database.getConnection();var sql=c.prepareStatement("INSERT INTO tournaments(id,owner_user_id,name,format,status,visibility) VALUES (?,?,'Private','ROUND_ROBIN','DRAFT','PRIVATE')")) {sql.setObject(1,id);sql.setObject(2,owner.id().value());sql.executeUpdate();}
        assertThat(repository.all()).noneMatch(t->t.id().equals(id));assertThat(restored().findById(id.toString())).isEmpty();
    }
}
