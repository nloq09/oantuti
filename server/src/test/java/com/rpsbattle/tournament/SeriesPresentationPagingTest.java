package com.rpsbattle.tournament;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SeriesPresentationPagingTest {
 @Test void groupedPagesNeverSplitAPlayedSeriesAndLegacyPagingRemainsAvailable(){
  var tournaments=mock(TournamentService.class);var t=new Tournament("cup","Cup",TournamentFormat.SINGLE_ELIMINATION,Instant.now());t.bestOf=5;
  var a=new TournamentParticipant("a",null,null,"An",1,UUID.randomUUID());var b=new TournamentParticipant("b",null,null,"Bình",2,UUID.randomUUID());
  for(int series=1;series<=2;series++)for(int game=1;game<=4;game++){var m=new TournamentMatch("s"+series+"-g"+game,t.id,a,b);m.seriesId="s"+series;m.gameNumber=game;t.matches.add(m);}
  when(tournaments.find(t.id)).thenReturn(t);when(tournaments.publicView(eq(t),anyList(),eq(true))).thenAnswer(call->Map.of("matches",call.getArgument(1),"series",List.of()));
  var service=new TournamentMatchPageService(tournaments,mock(AccountAuthorizationService.class));
  var first=service.page(null,t.id,0,1,null,null,null,false,"round",false,true);assertThat(first.get("total")).isEqualTo(2);assertThat((List<?>)first.get("items")).hasSize(4);
  var second=service.page(null,t.id,1,1,null,null,null,false,"round",false,true);assertThat(((List<TournamentMatch>)second.get("items"))).allMatch(m->m.seriesId.equals("s2"));
  var legacy=service.page(null,t.id,0,1,null,null,null,false,"round",false);assertThat(legacy.get("total")).isEqualTo(8);assertThat((List<?>)legacy.get("items")).hasSize(1);
 }
 @Test void summaryCountsRealReplaysEvenWhenCompletedMatchesAreNotInActivePage(){
  var service=new TournamentService(mock(com.rpsbattle.tournament.registry.TournamentRegistry.class),mock(com.rpsbattle.bot.upload.BotUploadService.class),mock(com.rpsbattle.tournament.scheduler.TournamentScheduler.class),mock(com.rpsbattle.tournament.ranking.RankingService.class));
  var t=new Tournament("cup","Cup",TournamentFormat.ROUND_ROBIN,Instant.now());
  var a=new TournamentParticipant("a",null,null,"An",1,UUID.randomUUID());var b=new TournamentParticipant("b",null,null,"Bình",2,UUID.randomUUID());
  var game=new TournamentMatch("g",t.id,a,b);game.status=TournamentMatch.Status.FINISHED;game.decisionReason="FORFEIT";t.matches.add(game);
  assertThat(service.summary(t).get("replayCount")).isEqualTo(0L);
  game.historyMatchId="history";
  var summary=service.summary(t);assertThat(summary.get("replayCount")).isEqualTo(1L);assertThat((List<?>)summary.get("matches")).isEmpty();
 }
}
