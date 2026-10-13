package com.rpsbattle.tournament.persistence;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.history.*;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(properties={"rps.bots.enabled=true","rps.tournament.bot-delay-millis=0"})
@Testcontainers(disabledWithoutDocker=true)
class DockerTournamentPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine").withLabel("rps.stage","stage4-group6-docker-test");
    @TempDir static Path root;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");registry.add("rps.bots.directory",root::toString);
    }
    @Autowired UserRepository users;
    @Autowired OwnedTournamentService owned;
    @Autowired OwnedBotService bots;
    @Autowired BotUploadService artifacts;
    @Autowired TournamentService tournaments;
    @Autowired TournamentRepository repository;
    @Autowired TournamentPersistenceCoordinator persistence;
    @Autowired MatchPersistenceCoordinator historyCoordinator;
    @Autowired MatchHistoryRepository history;
    @Autowired UserStatsService stats;
    User user() {var id=UserId.random();var now=Instant.now();var name="docker_"+id.value().toString().substring(0,8);return users.save(new User(id,name,name+"@example.com","unused","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null));}
    @Test void realDockerBotForfeitCommitsTournamentResultHistoryRankingAndAccountStats() throws Exception {
        assumeTrue(artifacts.available(BotLanguage.PYTHON),"Docker Linux/python image unavailable");
        var a=user();var b=user();var authA=AuthService.sessionAuthentication(a);var authB=AuthService.sessionAuthentication(b);
        var file=new MockMultipartFile("file","main.py","text/plain","raise SystemExit(1)\n".getBytes());
        var botA=bots.upload(authA,"Forfeit A",file);var botB=bots.upload(authB,"Forfeit B",file);
        var cup=owned.create(authA,"Docker persistence",TournamentFormat.ROUND_ROBIN,"PUBLIC");owned.add(authA,cup.id,botA.id().toString(),null,1L);owned.add(authB,cup.id,botB.id().toString(),null,2L);owned.start(authA,cup.id);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        boolean finished=false;
        while(System.nanoTime()<deadline) {synchronized(cup) {finished=cup.status==TournamentStatus.FINISHED;}if(finished) break;Thread.sleep(100);}
        assertThat(finished).isTrue();persistence.flushForRead();
        var registry=new TournamentRegistry();new TournamentPersistenceCoordinator(repository,registry,tournaments,historyCoordinator,artifacts).restore();var archive=registry.findById(cup.id).orElseThrow();
        assertThat(archive.matches).hasSize(1);assertThat(archive.matches.getFirst().status).isEqualTo(TournamentMatch.Status.FINISHED);assertThat(archive.matches.getFirst().failure).isEqualTo("BOT_FAILED");
        assertThat(history.find(archive.matches.getFirst().historyMatchId).orElseThrow().status()).isEqualTo("FINISHED");
        assertThat(stats.account(a.id().value()).matchesPlayed()).isEqualTo(1);assertThat(stats.account(b.id().value()).matchesPlayed()).isEqualTo(1);
        assertThat(stats.account(a.id().value()).wins()+stats.account(b.id().value()).wins()).isEqualTo(1);
        assertThat(tournaments.view(archive).get("champion")).isNotNull();
    }
}
