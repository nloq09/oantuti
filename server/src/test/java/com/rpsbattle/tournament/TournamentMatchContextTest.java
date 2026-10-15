package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TournamentMatchContextTest {
 @Test void contextIsReadOnlyHasAuthoritativeBo1AndExcludesSeatCapabilities(){
  var service=mock(TournamentService.class);var t=new Tournament("cup","Cup",TournamentFormat.SINGLE_ELIMINATION,Instant.now());
  var a=new TournamentParticipant("a","bot-a","secret-a","Alpha",1);var b=new TournamentParticipant("b","bot-b","secret-b","Beta",2);
  var m=new TournamentMatch("m",t.id,a,b);m.round=2;m.onlineMatchId="room";t.matches.add(m);t.rounds.addAll(List.of(new TournamentRound(1,List.of()),new TournamentRound(2,List.of())));
  when(service.find(t.id)).thenReturn(t);var context=new TournamentMatchContextService(service);var value=context.context(t.id,"room");
  assertThat(value).containsEntry("bestOf",1).containsEntry("gameNumber",1).containsEntry("round",2).containsEntry("totalRounds",2);
  assertThat(value.toString()).doesNotContain("secret-a","secret-b","bot-a");assertThat(t.matches).hasSize(1);assertThat(t.revision).isZero();
  assertThatThrownBy(()->context.context(t.id,"foreign-room")).hasMessageContaining("TOURNAMENT_MATCH_NOT_FOUND");
 }
}
