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
class TournamentExecutionServiceTest {
    @Test void oneFailedLaunchDoesNotPreventHealthyMatchesFinishing(){
        var registry=new TournamentRegistry();var t=registry.create("Mixed",TournamentFormat.ROUND_ROBIN);t.participants.addAll(TournamentFixtures.tournament(4).participants);t.matches.addAll(new RoundRobinScheduler().schedule(t));t.status=TournamentStatus.RUNNING;
        var executor=mock(TournamentMatchExecutor.class);var online=mock(OnlineMatchService.class);var room=new OnlineMatch("ROOM",MatchMode.HUMAN_VS_HUMAN,42,java.time.Clock.systemUTC(),java.time.Duration.ofSeconds(30));room.join();for(var p:java.util.List.of(OnlineMatch.P1,OnlineMatch.P2)){room.randomize(p);room.markReady(p);}room.submit(OnlineMatch.P2,room.state().sequence(),com.rpsbattle.game.action.SurrenderAction.of(OnlineMatch.P2));
        when(online.find(anyString())).thenReturn(room);var count=new java.util.concurrent.atomic.AtomicInteger();when(executor.start(any())).thenAnswer(i->{if(count.getAndIncrement()==0)throw new IllegalStateException("Docker");var m=(TournamentMatch)i.getArgument(0);m.status=TournamentMatch.Status.RUNNING;m.onlineMatchId=m.id;m.startedAt=java.time.Instant.now();return room;});
        var execution=new TournamentExecutionService(registry,mock(TournamentService.class),executor,online,mock(ReplayRegistry.class),4,900);execution.tick();execution.tick();execution.tick();
        assertThat(t.status).isEqualTo(TournamentStatus.FINISHED);assertThat(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED)).hasSize(5);assertThat(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FAILED)).hasSize(1);verify(online,times(5)).release("ROOM");
    }
    @Test void infrastructureFailureDoesNotKillRemainingSchedule(){var registry=new TournamentRegistry();var t=registry.create("Failure",TournamentFormat.ROUND_ROBIN);t.participants.addAll(TournamentFixtures.tournament(4).participants);t.matches.addAll(new RoundRobinScheduler().schedule(t));t.status=TournamentStatus.RUNNING;var executor=mock(TournamentMatchExecutor.class);when(executor.start(any())).thenThrow(new IllegalStateException("Docker failed"));var service=mock(TournamentService.class);var execution=new TournamentExecutionService(registry,service,executor,mock(OnlineMatchService.class),mock(ReplayRegistry.class),4,900);execution.tick();assertThat(t.matches).hasSize(6).allMatch(m->m.status==TournamentMatch.Status.FAILED);assertThat(t.status).isEqualTo(TournamentStatus.FINISHED);verify(executor,times(6)).start(any());verify(service,atLeastOnce()).changed(t);}
}
