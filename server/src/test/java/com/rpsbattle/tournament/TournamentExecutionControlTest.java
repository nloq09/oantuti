package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.execution.*;
import com.rpsbattle.online.*;
import com.rpsbattle.replay.ReplayRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TournamentExecutionControlTest {
    @Test void pausedTournamentFinishesExistingGamesButManualRoundNeedsExplicitRelease(){
        var f=new TournamentAdministrationTest();var t=f.cup(4,TournamentFormat.ROUND_ROBIN);t.operations=t.operations.rounds(false,1);
        var executor=mock(TournamentMatchExecutor.class);var online=mock(OnlineMatchService.class);
        var room=new OnlineMatch("ROOM",MatchMode.HUMAN_VS_HUMAN,42,java.time.Clock.systemUTC(),java.time.Duration.ofSeconds(30));room.join();for(var p:java.util.List.of(OnlineMatch.P1,OnlineMatch.P2)){room.randomize(p);room.markReady(p);}room.submit(OnlineMatch.P2,room.state().sequence(),com.rpsbattle.game.action.SurrenderAction.of(OnlineMatch.P2));
        when(online.find(anyString())).thenReturn(room);when(executor.start(any())).thenAnswer(i->{var m=(TournamentMatch)i.getArgument(0);m.onlineMatchId="ROOM";m.startedAt=java.time.Instant.now();return room;});
        var execution=new TournamentExecutionService(f.registry,f.service,executor,online,mock(ReplayRegistry.class),4,900);
        execution.tick();f.admin.control(f.auth,t.id,t.revision,"pause",null);execution.tick();
        assertThat(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED)).hasSize(2);verify(executor,times(2)).start(any());assertThat(t.status).isEqualTo(TournamentStatus.PAUSED);
        f.admin.control(f.auth,t.id,t.revision,"next-round",null);execution.tick();verify(executor,times(2)).start(any());f.admin.control(f.auth,t.id,t.revision,"resume",null);execution.tick();execution.tick();verify(executor,times(4)).start(any());
        assertThat(t.matches.stream().filter(m->m.round==3)).allMatch(m->m.status==TournamentMatch.Status.PENDING);
    }
    @Test void failedCompletionCommitStopsSchedulingAndPublishesNoPartialBracket(){
        var f=new TournamentAdministrationTest();var t=f.cup(4,TournamentFormat.SINGLE_ELIMINATION);
        for(var m:t.matches){m.status=TournamentMatch.Status.PENDING;}t.operations=t.operations.participant(t.matches.getFirst().participantA.participantId(),ParticipantStatus.DISQUALIFIED);
        var coordinator=mock(com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator.class);doThrow(new org.springframework.dao.DataAccessResourceFailureException("outage")).when(coordinator).persistNow(t);
        var executor=mock(TournamentMatchExecutor.class);var notified=new java.util.concurrent.atomic.AtomicInteger();f.service.listen(cup->notified.incrementAndGet());
        var execution=new TournamentExecutionService(f.registry,f.service,executor,mock(OnlineMatchService.class),mock(ReplayRegistry.class),4,900);execution.persistence(coordinator);execution.tick();
        assertThat(t.status).isEqualTo(TournamentStatus.RECOVERY_REQUIRED);verifyNoInteractions(executor);assertThat(notified.get()).isZero();assertThat(t.operations.audit().getLast().type()).isEqualTo("PERSISTENCE_ERROR");
    }
}
