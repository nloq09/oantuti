package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.*;
import com.rpsbattle.tournament.scheduler.*;
import com.rpsbattle.tournament.execution.*;
import com.rpsbattle.online.*;
import com.rpsbattle.replay.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TournamentConcurrencyTest {
    @Test void globalLimitCoversAllTournamentsAndCancelledWorkIsReleased(){var registry=new TournamentRegistry();var service=mock(TournamentService.class);var executor=mock(TournamentMatchExecutor.class);var online=mock(OnlineMatchService.class);var replays=mock(ReplayRegistry.class);var room=mock(OnlineMatch.class);var state=mock(com.rpsbattle.game.state.GameState.class);when(room.state()).thenReturn(state);when(room.id()).thenReturn("ROOM");when(online.find(anyString())).thenReturn(room);when(executor.start(any())).thenAnswer(i->{var m=(TournamentMatch)i.getArgument(0);m.status=TournamentMatch.Status.RUNNING;m.onlineMatchId=m.id;m.startedAt=java.time.Instant.now();return room;});
        for(int j=0;j<2;j++){var t=registry.create("T"+j,TournamentFormat.ROUND_ROBIN);t.participants.addAll(TournamentFixtures.tournament(4).participants);t.matches.addAll(new RoundRobinScheduler().schedule(t));t.status=TournamentStatus.RUNNING;}
        var execution=new TournamentExecutionService(registry,service,executor,online,replays,4,900);execution.tick();execution.tick();assertThat(registry.findAll().stream().flatMap(t->t.matches.stream()).filter(m->m.status==TournamentMatch.Status.RUNNING)).hasSize(4);verify(executor,times(4)).start(any());for(var t:registry.findAll())t.status=TournamentStatus.CANCELLED;execution.tick();assertThat(registry.findAll().stream().flatMap(t->t.matches.stream())).allMatch(m->m.status==TournamentMatch.Status.FAILED);verify(online,times(4)).release("ROOM");
    }
}
