package com.rpsbattle.history;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.history.domain.MatchMetadata;
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

@SpringBootTest(properties={"rps.bots.enabled=false","rps.history.flush-millis=60000"})
@Testcontainers(disabledWithoutDocker=true)
class MatchHistoryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine").withLabel("rps.stage","stage4-group5-test");
    @TempDir static Path root;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");registry.add("rps.bots.directory",root::toString);
    }
    @Autowired MatchHistoryRepository matches;
    @Autowired UserStatsService stats;
    @Autowired UserRepository users;
    @Autowired OwnedBotService bots;
    User user() {var now=Instant.now();var id=UserId.random();var name="history_"+id.value().toString().substring(0,8);return users.save(new User(id,name,name+"@example.com","unused","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null));}
    MatchMetadata record(UUID a,UUID b,String winner,int turns,UUID bot,String status) {
        var start=Instant.parse("2026-10-13T00:00:00Z");var roster=List.of(new MatchMetadata.Participant("P1",a,bot,bot==null?MatchMetadata.ControllerType.HUMAN:MatchMetadata.ControllerType.UPLOADED_BOT),new MatchMetadata.Participant("P2",b,null,b==null?MatchMetadata.ControllerType.BUILTIN_BOT:MatchMetadata.ControllerType.HUMAN));
        boolean finished=status.equals("FINISHED");
        return new MatchMetadata(UUID.randomUUID().toString(),"SAMECODE",b==null?"HUMAN_VS_BOT":"HUMAN_VS_HUMAN",status,a,b,!finished||winner==null?null:winner.equals("P1")?a:b,finished?winner:null,finished?(winner==null?"DRAW":"WIN"):null,turns,start,finished?start.plusSeconds(2):null,finished?2000L:null,"classic-online-v1",start,roster);
    }
    @Test void accountFiltersPaginationAndStatsIncludeGuestWinnerAndExcludeSelfPlayOrFailures() {
        var a=user().id().value();var b=user().id().value();
        matches.save(record(a,b,"P1",3,null,"FINISHED"));matches.save(record(a,b,null,4,null,"FINISHED"));matches.save(record(a,null,"P2",5,null,"FINISHED"));
        assertThat(matches.history(a,"all",0,2).matches()).hasSize(2);assertThat(matches.history(a,"all",1,2).matches()).hasSize(1);
        assertThat(matches.history(a,"all",0,2).total()).isEqualTo(3);
        for(var filter:List.of("win","loss","draw","bot")) assertThat(matches.history(a,filter,0,20).total()).as(filter).isEqualTo(1);
        assertThat(matches.history(a,"human",0,20).total()).isEqualTo(2);
        matches.save(record(a,a,"P1",40,null,"FINISHED"));matches.save(record(a,b,null,80,null,"INFRASTRUCTURE_ERROR"));matches.save(record(a,b,null,90,null,"PLAYING"));
        var totals=stats.account(a);assertThat(totals.matchesPlayed()).isEqualTo(3);assertThat(totals.wins()).isEqualTo(1);assertThat(totals.losses()).isEqualTo(1);assertThat(totals.draws()).isEqualTo(1);assertThat(totals.winRate()).isEqualTo(33.33);assertThat(totals.totalTurns()).isEqualTo(12);
        assertThat(matches.history(user().id().value(),"all",0,20).total()).isZero();assertThat(stats.account(user().id().value()).winRate()).isZero();
    }
    @Test void duplicateFinalizationAndStaleSnapshotsNeverOverwriteResultOrDoubleCount() {
        var a=user().id().value();var b=user().id().value();var finished=record(a,b,"P1",8,null,"FINISHED");
        matches.save(finished);matches.save(finished);
        var stale=new MatchMetadata(finished.id(),finished.roomId(),finished.mode(),"WAITING",null,null,null,null,null,0,null,null,null,finished.ruleSet(),finished.createdAt(),List.of());
        matches.save(stale);assertThat(matches.find(finished.id()).orElseThrow()).isEqualTo(finished);
        assertThat(stats.account(a).matchesPlayed()).isEqualTo(1);assertThat(stats.account(a).wins()).isEqualTo(1);assertThat(stats.account(b).losses()).isEqualTo(1);
    }
    @Test void ownerAndUploadedControllerPersistSeparatelyAndSoftDeletedBotsKeepHistory() {
        var owner=user();var opponent=user();var auth=AuthService.sessionAuthentication(owner);
        var bot=bots.upload(auth,"History bot",new MockMultipartFile("file","main.py","text/plain","print(1)".getBytes()));
        var metadata=record(owner.id().value(),opponent.id().value(),"P1",7,bot.id(),"FINISHED");matches.save(metadata);bots.delete(auth,bot.id().toString());
        var reopened=matches.find(metadata.id()).orElseThrow();assertThat(reopened.participants().getFirst().userId()).isEqualTo(owner.id().value());assertThat(reopened.participants().getFirst().botId()).isEqualTo(bot.id());assertThat(reopened.participants().getFirst().controllerType()).isEqualTo(MatchMetadata.ControllerType.UPLOADED_BOT);
        assertThat(matches.history(owner.id().value(),"bot",0,20).total()).isEqualTo(1);assertThat(stats.account(owner.id().value()).wins()).isEqualTo(1);
    }
    @Test void archiveIdentityDoesNotCollideWhenPublicRoomCodesAreReused() {
        var a=user().id().value();var b=user().id().value();var first=record(a,b,"P1",1,null,"FINISHED");var second=record(a,b,"P2",2,null,"FINISHED");
        matches.save(first);matches.save(second);assertThat(first.roomId()).isEqualTo(second.roomId());assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(matches.find(first.id())).isPresent();assertThat(matches.find(second.id())).isPresent();assertThat(stats.account(a).matchesPlayed()).isEqualTo(2);
    }
}
