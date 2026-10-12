package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.*;
import com.rpsbattle.tournament.scheduler.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.online.DemoRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TournamentServiceTest {
    @Test void validatesBotTokenAndDuplicateBeforeStartAndLocksStartedRoster(){var bots=mock(BotUploadService.class);when(bots.authorize(anyString(),eq("valid"))).thenAnswer(i->new BotUploadService.Upload(i.getArgument(0),"Bot",BotLanguage.PYTHON,"valid"));when(bots.authorize(anyString(),eq("wrong"))).thenThrow(new DemoRequestException("BOT_ACCESS_DENIED"));when(bots.available(any())).thenReturn(true);var service=new TournamentService(new TournamentRegistry(),bots,new RoundRobinScheduler(),new RankingService(new ClassicScoringRule()));var t=service.create("Test",TournamentFormat.ROUND_ROBIN);assertThatThrownBy(()->service.start(t.id)).hasMessage("NOT_ENOUGH_BOTS");assertThatThrownBy(()->service.add(t.id,"B1","wrong","A",1L)).hasMessage("BOT_ACCESS_DENIED");service.add(t.id,"B1","valid","A",1L);assertThatThrownBy(()->service.add(t.id,"B1","valid","A",1L)).hasMessage("DUPLICATE_PARTICIPANT");service.add(t.id,"B2","valid","B",2L);service.start(t.id);assertThat(t.matches).hasSize(1);assertThatThrownBy(()->service.start(t.id)).hasMessage("TOURNAMENT_LOCKED");assertThatThrownBy(()->service.remove(t.id,t.participants.getFirst().participantId())).hasMessage("TOURNAMENT_LOCKED");}
    @Test void credentialsNeverAppearInViewSerialization() throws Exception {var t=TournamentFixtures.tournament(2);var service=new TournamentService(new TournamentRegistry(),mock(BotUploadService.class),new RoundRobinScheduler(),new RankingService(new ClassicScoringRule()));t.matches.addAll(new RoundRobinScheduler().schedule(t));var json=new ObjectMapper().findAndRegisterModules().writeValueAsString(service.view(t));assertThat(json).doesNotContain("secret-","botToken");}
}
