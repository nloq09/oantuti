package com.rpsbattle.operations;

import com.rpsbattle.worker.WorkerManager;
import com.rpsbattle.tournament.execution.TournamentExecutionService;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import com.rpsbattle.history.MatchPersistenceCoordinator;
import com.rpsbattle.replay.ReplayArchiveStore;
import com.rpsbattle.online.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class GracefulMatchShutdownTest {
    @Test void checkpointsBeforeClosingBotsEvenIfOnePersistenceStoreFails() {
        var workers=mock(WorkerManager.class);var execution=mock(TournamentExecutionService.class);var online=mock(OnlineMatchService.class);
        var rooms=new MatchRegistry();var room=new OnlineMatch("ROOM",MatchMode.HUMAN_VS_HUMAN,42L,Clock.systemUTC(),Duration.ofSeconds(30));rooms.register(room);
        var history=mock(MatchPersistenceCoordinator.class);var tournaments=mock(TournamentPersistenceCoordinator.class);var replays=mock(ReplayArchiveStore.class);
        doThrow(new IllegalStateException("database unavailable")).when(history).flush();
        var lifecycle=new GracefulMatchShutdown(workers,execution,online,rooms,history,tournaments,replays);
        lifecycle.start();lifecycle.stop();lifecycle.stop();
        var ordered=inOrder(online,workers,history,execution,tournaments,replays);
        ordered.verify(online).stopAccepting();ordered.verify(workers).drain();ordered.verify(history).capture(room);
        ordered.verify(execution).checkpointShutdown();ordered.verify(history).flush();ordered.verify(tournaments).flush();ordered.verify(replays).flush();ordered.verify(online).shutdown();
        assertThat(room.infrastructureError()).isEqualTo("MATCH_INFRASTRUCTURE_ERROR");assertThat(lifecycle.isRunning()).isFalse();
        verify(online,times(1)).shutdown();
    }
}
