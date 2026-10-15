package com.rpsbattle.tournament;
import com.rpsbattle.account.*;import com.rpsbattle.account.domain.*;import com.rpsbattle.auth.AuthService;import com.rpsbattle.tournament.domain.*;import org.junit.jupiter.api.Test;import java.time.Instant;import java.util.*;import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class ParticipantTournamentStateTest {
 @Test void selfViewExcludesOtherOwnersAndTracksApprovalCheckInAndNextOpponent(){
  var now=Instant.now();var user=new User(UserId.random(),"player","player@example.invalid","secret","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null,0);var auth=AuthService.sessionAuthentication(user);
  var tournaments=mock(TournamentService.class);var owned=mock(OwnedTournamentService.class);var accounts=mock(AccountAuthorizationService.class);when(accounts.requireUser(auth)).thenReturn(user);
  var t=new Tournament(UUID.randomUUID().toString(),"State fixture",TournamentFormat.ROUND_ROBIN,now);t.status=TournamentStatus.CHECK_IN;t.checkInRequired=true;
  var mine=UUID.randomUUID();var foreign=UUID.randomUUID();var bot=UUID.randomUUID();t.registrations.add(new TournamentRegistration(mine,UUID.fromString(t.id),user.id().value(),bot,bot,"Mine",TournamentRegistration.Status.APPROVED,now,now,null));t.registrations.add(new TournamentRegistration(foreign,UUID.fromString(t.id),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Other private registration",TournamentRegistration.Status.PENDING,now,null,null));
  var a=new TournamentParticipant(mine.toString(),bot.toString(),"never-public","Mine",1,user.id().value());var b=new TournamentParticipant(foreign.toString(),UUID.randomUUID().toString(),"never-public","Opponent",2,UUID.randomUUID());t.participants.addAll(List.of(a,b));var match=new TournamentMatch(UUID.randomUUID().toString(),t.id,a,b);t.matches.add(match);
  when(tournaments.find(t.id)).thenReturn(t);when(tournaments.view(t)).thenReturn(new HashMap<>());when(tournaments.ranking(t)).thenReturn(List.of());var service=new ParticipantTournamentStateService(tournaments,owned,accounts);
  assertThat(service.me(auth,t.id).toString()).contains("CHECK_IN_REQUIRED").doesNotContain("Other private registration","never-public");
  t.status=TournamentStatus.RUNNING;assertThat(service.me(auth,t.id).toString()).contains("WAITING_MATCH","Opponent");match.status=TournamentMatch.Status.RUNNING;assertThat(service.me(auth,t.id).toString()).contains("PLAYING");
 }
}
