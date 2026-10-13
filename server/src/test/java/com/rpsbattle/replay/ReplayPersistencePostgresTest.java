package com.rpsbattle.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.history.*;
import com.rpsbattle.online.*;
import com.rpsbattle.game.action.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker=true)
class ReplayPersistencePostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @TempDir static Path root;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("rps.replays.directory",root::toString);
    }
    @Autowired OnlineMatchService online;
    @Autowired ReplayRegistry replays;
    @Autowired ReplayArchiveStore archives;
    @Autowired ReplayRepository repository;
    @Autowired MatchHistoryRepository history;
    @Autowired ObjectMapper json;
    @Autowired GameStateMapper mapper;
    @Autowired DataSource database;
    OnlineMatch finished(Long seed) {
        var room=online.create(MatchMode.HUMAN_VS_HUMAN,seed);room.join();
        for(var id:List.of(OnlineMatch.P1,OnlineMatch.P2)) {room.randomize(id);room.markReady(id);}
        online.publish(room,room.submit(OnlineMatch.P1,room.state().sequence(),PassAction.of(OnlineMatch.P1)));
        online.publish(room,room.submit(OnlineMatch.P2,room.state().sequence(),SurrenderAction.of(OnlineMatch.P2)));
        return room;
    }
    ReplayArchiveStore reopened() {return new ReplayArchiveStore(repository,json,mapper,root.toString());}
    @Test void offTurnSurrenderPersistsAndReplaysWithoutRelaxingMoveValidation() {
        var room=online.create(MatchMode.HUMAN_VS_HUMAN,42L);room.join();
        for(var id:List.of(OnlineMatch.P1,OnlineMatch.P2)) {room.randomize(id);room.markReady(id);}
        online.publish(room,room.submit(OnlineMatch.P2,room.state().sequence(),SurrenderAction.of(OnlineMatch.P2)));
        assertThat(replays.view(room.archiveId()).get("result")).isEqualTo(Map.of("outcome","WIN","winner","P1"));
        var events=new ArrayList<>(room.events());
        events.set(2,new com.rpsbattle.game.engine.GameEvent.TurnChanged(events.get(2).sequence(),OnlineMatch.P1,OnlineMatch.P2,1));
        assertThatThrownBy(()->new ReplayService().replay(room.replayLog().initialState(),events)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Non-terminal action outside turn");
    }
    @Test void replayAndSpectatorSnapshotSurviveNewStoreWithoutLiveRoom() throws Exception {
        var room=finished(42L);var expected=replays.view(room.id());archives.flush();online.release(room.id());
        var restored=new ReplayRegistry(online,mapper,reopened());
        assertThat(restored.view(room.archiveId())).isEqualTo(expected);
        assertThat(restored.view(room.id())).isEqualTo(expected);
        assertThat(restored.finalSnapshot(room.archiveId()).gameState().phase()).isEqualTo("FINISHED");
        assertThat(history.find(room.archiveId())).isPresent();
        var text=json.writeValueAsString(expected);
        assertThat(text).doesNotContain("token","filePath","sha256","password");
        assertThat(repository.find(room.archiveId()).orElseThrow().eventCount()).isEqualTo(room.events().size());
    }
    @Test void duplicateCompletionDoesNotDuplicateMetadataOrFiles() throws Exception {
        var room=finished(42L);replays.save(room,online);replays.save(room,online);archives.flush();
        try(var c=database.getConnection();var sql=c.prepareStatement("select count(*) from replays where match_id=?")) {
            sql.setString(1,room.archiveId());try(var rows=sql.executeQuery()){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
        }
        assertThat(Files.exists(root.resolve(room.archiveId()+".json"))).isTrue();
    }
    @Test void nullableSeedRemainsReadable() {
        var room=finished(1L);var original=room.replayLog();
        var log=new MatchLog(original.matchId(),original.ruleSet(),null,original.initialState(),original.events(),original.result());
        var path=root.resolve("nullable-log.json");var service=new ReplayService();service.write(path,log);
        assertThat(service.read(path).seed()).isNull();assertThat(service.replay(service.read(path)).result()).contains(log.result());
    }
    @Test void reusedRoomCodeNeverFallsBackToAnOlderFinishedReplay() {
        var room=finished(1L);archives.flush();var previous=MatchMetadataMapper.capture(room);
        var newer=new com.rpsbattle.history.domain.MatchMetadata(UUID.randomUUID().toString(),room.id(),previous.mode(),"PLACEMENT",null,null,null,null,null,0,null,null,null,previous.ruleSet(),previous.createdAt().plusSeconds(60),List.of());
        history.save(newer);
        assertThat(repository.find(room.id())).isEmpty();assertThat(repository.find(room.archiveId())).isPresent();
    }
    @Test void missingFileIsReportedAndMarkedInvalid() throws Exception {
        var room=finished(1L);archives.flush();Files.delete(root.resolve(room.archiveId()+".json"));
        assertThatThrownBy(()->reopened().load(room.archiveId())).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).code()).isEqualTo("REPLAY_FILE_MISSING"));
        assertThat(repository.find(room.archiveId()).orElseThrow().valid()).isFalse();
    }
    @Test void changedFileIsRejectedInsteadOfServingRamCache() throws Exception {
        var room=finished(1L);archives.flush();Files.writeString(root.resolve(room.archiveId()+".json"),"{}");
        assertThatThrownBy(()->replays.view(room.id())).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).code()).isEqualTo("REPLAY_INVALID"));
        assertThat(repository.find(room.archiveId()).orElseThrow().valid()).isFalse();
    }
    @Test void databasePathCannotEscapeReplayDirectory() throws Exception {
        var room=finished(1L);archives.flush();
        try(var c=database.getConnection();var sql=c.prepareStatement("update replays set file_path='../outside.json' where match_id=?")) {sql.setString(1,room.archiveId());sql.executeUpdate();}
        assertThatThrownBy(()->reopened().load(room.archiveId())).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(422));
    }
    @Test void dbFailureRetainsCapturedLogForRetryAfterRoomRelease() {
        var room=finished(1L);archives.flush();var failing=mock(ReplayRepository.class);
        doThrow(new IllegalStateException("offline")).doNothing().when(failing).save(any(),any());
        var store=new ReplayArchiveStore(failing,json,mapper,root.toString());
        store.capture(MatchMetadataMapper.capture(room),room.replayLog(),online.snapshot(room,OnlineMatch.P1,List.of()));online.release(room.id());
        assertThatThrownBy(store::flush).isInstanceOf(IllegalStateException.class);store.flush();store.flush();
        verify(failing,times(2)).save(any(),any());
    }
}
