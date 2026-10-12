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
    @Test void executionUsesOnlineHumanModeTwoExistingBotSeatsAndAutomaticReady(){var registry=new MatchRegistry();var online=new OnlineMatchService(registry,new GameStateMapper(),Clock.systemUTC(),30);var bots=mock(BotUploadService.class);when(bots.agent(anyString(),anyString())).thenAnswer(i->mock(BotAgent.class));var entry=new RoundRobinScheduler().schedule(TournamentFixtures.tournament(2)).getFirst();try{var room=new TournamentMatchExecutor(online,bots,0).start(entry);assertThat(room.mode()).isEqualTo(MatchMode.HUMAN_VS_HUMAN);assertThat(room.status()).isEqualTo("PLAYING");assertThat(room.state().aliveCount(OnlineMatch.P1)).isEqualTo(18);assertThat(room.state().aliveCount(OnlineMatch.P2)).isEqualTo(18);assertThat(room.ready(OnlineMatch.P1)&&room.ready(OnlineMatch.P2)).isTrue();online.release(room.id());assertThat(registry.getActiveMatches()).isEmpty();}finally{online.shutdown();}}
}
