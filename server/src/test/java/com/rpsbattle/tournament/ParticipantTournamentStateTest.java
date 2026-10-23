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
 @Test void rejectionReasonIsPersonalAndDoesNotRequirePublishingAudit(){
  var now=Instant.now();var user=new User(UserId.random(),"player","player@example.invalid","secret","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null,0);var auth=AuthService.sessionAuthentication(user);
  var service=spy(new TournamentService(null,null,null,mock(com.rpsbattle.tournament.ranking.RankingService.class)));var owned=mock(OwnedTournamentService.class);var accounts=mock(AccountAuthorizationService.class);when(accounts.requireUser(auth)).thenReturn(user);
  var t=new Tournament(UUID.randomUUID().toString(),"Cup",TournamentFormat.ROUND_ROBIN,now);t.status=TournamentStatus.REGISTRATION;
  var mine=UUID.randomUUID();var other=UUID.randomUUID();
  t.registrations.add(new TournamentRegistration(mine,UUID.fromString(t.id),user.id().value(),null,null,"Mine",TournamentRegistration.Status.REJECTED,now,null,null));
  t.registrations.add(new TournamentRegistration(other,UUID.fromString(t.id),UUID.randomUUID(),null,null,"Other",TournamentRegistration.Status.REJECTED,now,null,null));
  TournamentAudit.append(t,"REGISTRATION_UPDATED",null,mine.toString(),"Thiếu thông tin trường học","PENDING","REJECTED");
  TournamentAudit.append(t,"REGISTRATION_UPDATED",null,other.toString(),"Other private reason","PENDING","REJECTED");
  doReturn(t).when(service).find(t.id);
  var result=new ParticipantTournamentStateService(service,owned,accounts).me(auth,t.id);
  assertThat(result.toString()).contains("rejectionReason=Thiếu thông tin trường học").doesNotContain("Other private reason");
  assertThat(service.summary(t)).doesNotContainKey("audit");
 }
}
