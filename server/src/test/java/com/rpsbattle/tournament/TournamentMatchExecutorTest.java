package com.rpsbattle.tournament;
import com.rpsbattle.online.*;
import com.rpsbattle.bot.*;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.tournament.execution.*;
import com.rpsbattle.tournament.scheduler.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TournamentMatchExecutorTest {
    @Test void humanParticipantsUseExistingPlacementAndNeverLaunchDocker(){var registry=new MatchRegistry();var online=new OnlineMatchService(registry,new GameStateMapper(),Clock.systemUTC(),30);var bots=mock(BotUploadService.class);var a=new com.rpsbattle.tournament.domain.TournamentParticipant(java.util.UUID.randomUUID().toString(),null,"","Human A",1,java.util.UUID.randomUUID());var b=new com.rpsbattle.tournament.domain.TournamentParticipant(java.util.UUID.randomUUID().toString(),null,"","Human B",2,java.util.UUID.randomUUID());var entry=new com.rpsbattle.tournament.domain.TournamentMatch(java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString(),a,b);try{var room=new TournamentMatchExecutor(online,bots,0,mock(com.rpsbattle.history.MatchSeatService.class)).start(entry);assertThat(room.seats()).hasSize(2);assertThat(room.status()).isEqualTo("PLACEMENT");assertThat(room.ready(OnlineMatch.P1)).isFalse();assertThat(room.ready(OnlineMatch.P2)).isFalse();verifyNoInteractions(bots);online.release(room.id());}finally{online.shutdown();}}
    @Test void executionUsesOnlineHumanModeTwoExistingBotSeatsAndAutomaticReady(){var registry=new MatchRegistry();var online=new OnlineMatchService(registry,new GameStateMapper(),Clock.systemUTC(),30);var bots=mock(BotUploadService.class);when(bots.agent(anyString(),anyString())).thenAnswer(i->mock(BotAgent.class));var entry=new RoundRobinScheduler().schedule(TournamentFixtures.tournament(2)).getFirst();try{var room=new TournamentMatchExecutor(online,bots,0).start(entry);assertThat(room.mode()).isEqualTo(MatchMode.HUMAN_VS_HUMAN);assertThat(room.status()).isEqualTo("PLAYING");assertThat(room.state().aliveCount(OnlineMatch.P1)).isEqualTo(18);assertThat(room.state().aliveCount(OnlineMatch.P2)).isEqualTo(18);assertThat(room.ready(OnlineMatch.P1)&&room.ready(OnlineMatch.P2)).isTrue();online.release(room.id());assertThat(registry.getActiveMatches()).isEmpty();}finally{online.shutdown();}}
}
