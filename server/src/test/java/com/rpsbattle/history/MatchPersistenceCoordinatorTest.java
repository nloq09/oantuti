package com.rpsbattle.history;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.online.*;
import com.rpsbattle.history.domain.MatchMetadata;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchPersistenceCoordinatorTest {
    @Test void persistenceFailureRetainsLatestFinishEvenAfterRoomRelease() {
        var registry=new MatchRegistry();var service=new OnlineMatchService(registry,new GameStateMapper(),Clock.systemUTC(),30);
        var repository=mock(MatchHistoryRepository.class);var coordinator=new MatchPersistenceCoordinator(service,registry,repository);coordinator.subscribe();
        try {
            var room=service.create(MatchMode.HUMAN_VS_HUMAN,42L);room.bindSeat(OnlineMatch.P1,UUID.randomUUID(),null);room.join();room.bindSeat(OnlineMatch.P2,UUID.randomUUID(),null);
            for(var player:List.of(OnlineMatch.P1,OnlineMatch.P2)) {room.randomize(player);room.markReady(player);}
            service.publish(room,List.of());
            when(repository.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated outage"));
            assertThatThrownBy(coordinator::flushForRead).isInstanceOf(com.rpsbattle.auth.AuthException.class).hasMessage("MATCH_PERSISTENCE_UNAVAILABLE");
            service.publish(room,room.leave(OnlineMatch.P1));service.release(room.id());assertThat(registry.getActiveMatches()).isEmpty();
            doAnswer(call->{assertThat(Thread.holdsLock(room)).isFalse();return call.getArgument(0);}).when(repository).save(any());
            coordinator.flush();
            var saved=org.mockito.ArgumentCaptor.forClass(MatchMetadata.class);verify(repository,times(2)).save(saved.capture());
            assertThat(saved.getValue().status()).isEqualTo("FINISHED");assertThat(saved.getValue().winnerSeat()).isEqualTo("P2");
            coordinator.flush();verify(repository,times(2)).save(any());
        } finally {service.shutdown();}
    }
    @Test void newerCaptureDuringWriteIsNotRemovedWithOldSnapshot() {
        var registry=new MatchRegistry();var service=new OnlineMatchService(registry,new GameStateMapper(),Clock.systemUTC(),30);
        var repository=mock(MatchHistoryRepository.class);var coordinator=new MatchPersistenceCoordinator(service,registry,repository);coordinator.subscribe();
        try {
            var room=service.create(MatchMode.HUMAN_VS_HUMAN,42L);var first=new AtomicBoolean(true);
            when(repository.save(any())).thenAnswer(call->{
                assertThat(Thread.holdsLock(room)).isFalse();
                if(first.getAndSet(false)) {room.join();service.publish(room,List.of());}
                return call.getArgument(0);
            });
            coordinator.flush();coordinator.flush();
            var saved=org.mockito.ArgumentCaptor.forClass(MatchMetadata.class);verify(repository,times(2)).save(saved.capture());
            assertThat(saved.getAllValues().getFirst().participants()).hasSize(1);assertThat(saved.getValue().participants()).hasSize(2);
        } finally {service.shutdown();}
    }
}
