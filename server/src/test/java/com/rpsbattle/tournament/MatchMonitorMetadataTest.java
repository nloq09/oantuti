package com.rpsbattle.tournament;
import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.bot.ownership.*;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchMonitorMetadataTest {
 @Test void repeatedPageSidesReadEachBotOnlyOnceAndDoNotCacheAcrossRequests(){
  var tournaments=mock(TournamentService.class);var accounts=mock(AccountAuthorizationService.class);var remote=mock(RemoteExecutionReadService.class);var bots=mock(BotRepository.class);var auth=mock(Authentication.class);UUID owner=UUID.randomUUID(),bot=UUID.randomUUID();
  var user=new User(new UserId(owner),"owner","owner@example.com","hash","Owner",UserRole.USER,AccountStatus.ACTIVE,Instant.now(),Instant.now(),null);when(accounts.requireUser(auth)).thenReturn(user);
  var t=new Tournament(UUID.randomUUID().toString(),"Metadata fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=owner;
  var a=new TournamentParticipant(UUID.randomUUID().toString(),bot.toString(),"secret","A",1,owner);var b=new TournamentParticipant(UUID.randomUUID().toString(),bot.toString(),"secret","B",2,owner);
  for(int i=0;i<60;i++)t.matches.add(new TournamentMatch(UUID.randomUUID().toString(),t.id,a,b));when(tournaments.find(t.id)).thenReturn(t);when(remote.forMatches(eq(t.id),anySet())).thenReturn(Map.of());when(bots.find(bot)).thenReturn(Optional.empty());
  var service=new MatchMonitorService(tournaments,mock(TournamentAuthorizationService.class),accounts,remote,bots);var first=service.page(auth,t.id,0,50,"ALL",true);assertThat((List<?>)first.get("items")).hasSize(50);verify(bots,times(1)).find(bot);
  service.page(auth,t.id,1,50,"ALL",true);verify(bots,times(2)).find(bot);
  service.page(auth,t.id,0,50,"FINISHED",true);verify(bots,times(2)).find(bot);
 }
}
