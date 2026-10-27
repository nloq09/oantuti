package com.rpsbattle.tournament;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerReadPerformanceTest {
    final UserRepository users=mock(UserRepository.class);
    final TournamentRegistry registry=new TournamentRegistry();
    final TournamentService service=new TournamentService(registry,null,null,new RankingService(new ClassicScoringRule()));
    PlayerReadPerformanceTest(){service.users(users);when(users.displayNames(any())).thenAnswer(call->{var names=new HashMap<UUID,String>();for(var id:(Collection<UUID>)call.getArgument(0))names.put(id,"Public name");return names;});}
    Tournament cup(int count){var t=registry.create("Player read fixture",TournamentFormat.SINGLE_ELIMINATION);t.ownerUserId=UUID.randomUUID();t.maxParticipants=4000;for(int i=0;i<count;i++)t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"private-bot-token","Player "+i,i,UUID.randomUUID()));registry.updateMemberships(t);return t;}
    @Test void fourThousandPlayerSummaryHasBoundedPayloadAndOneOwnerProjection() throws Exception {
        var t=cup(4000);var summary=service.playerSummary(t);var body=new ObjectMapper().findAndRegisterModules().writeValueAsBytes(summary);
        assertThat(body.length).isLessThan(20_000);assertThat(summary.get("participantCount")).isEqualTo(4000);
        assertThat((List<?>)summary.get("participants")).isEmpty();assertThat((List<?>)summary.get("ranking")).isEmpty();assertThat((List<?>)summary.get("registrations")).isEmpty();
        verify(users).displayNames(Set.of(t.ownerUserId));verify(users,never()).findById(any());assertThat(new String(body,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("private-bot-token","ownerToken","seedingSeed","Player 3999");
    }
    @Test void legacyProjectionUsesOneBatchAndKeepsPublicOwnerLabels(){
        var t=cup(1000);var summary=service.summary(t);assertThat((Map<?,?>)summary.get("participantOwners")).hasSize(1000);verify(users,times(1)).displayNames(argThat(ids->ids.size()==1001));verify(users,never()).findById(any());
    }
    @Test void personalReadDoesNotBuildGlobalSummaryOrForcePersistence(){
        var t=cup(1000);var actor=new User(UserId.random(),"player","player@example.test","hash","Player",UserRole.USER,AccountStatus.ACTIVE,Instant.now(),Instant.now(),null,0);var auth=AuthService.sessionAuthentication(actor);var accounts=mock(AccountAuthorizationService.class);when(accounts.requireUser(auth)).thenReturn(actor);var owned=mock(OwnedTournamentService.class);var spy=spy(service);
        var result=new ParticipantTournamentStateService(spy,owned,accounts).me(auth,t.id);assertThat((List<?>)result.get("states")).isEmpty();verify(spy,never()).summary(any());verify(spy,never()).ranking(any());verify(owned,never()).flush();verifyNoInteractions(users);
    }
    @Test void publicPagesAreBoundedAndKeepOriginalRankPositionsWithoutPrivateFields() throws Exception {
        var t=cup(1000);var pages=new PlayerTournamentPageService(service,users);var participants=pages.page(t.id,"participants",2,20);
        assertThat((List<?>)participants.get("items")).hasSize(20);assertThat(participants.get("total")).isEqualTo(1000L);verify(users).displayNames(argThat(ids->ids.size()==20));
        var ranking=pages.page(t.id,"ranking",1,20);var rows=(List<Map<String,Object>>)ranking.get("items");assertThat(rows.getFirst().get("rank")).isEqualTo(21);assertThat(rows.getLast().get("rank")).isEqualTo(40);
        var body=new ObjectMapper().findAndRegisterModules().writeValueAsString(participants);assertThat(body).doesNotContain("private-bot-token","email","password","artifactHash","ownerUserId","botId");
        assertThatThrownBy(()->pages.page(t.id,"participants",-1,20)).hasMessage("INVALID_PAGE");assertThatThrownBy(()->pages.page(t.id,"ranking",0,51)).hasMessage("INVALID_PAGE");assertThatThrownBy(()->pages.page(t.id,"audit",0,20)).hasMessage("INVALID_FILTER");
    }
    @Test void lightweightFinalKeepsChampionAndRunnerUpAndLateDisqualificationRemovesChampion(){
        var t=cup(4);var a=t.participants.get(0);var b=t.participants.get(1);var game=new TournamentMatch(UUID.randomUUID().toString(),t.id,a,b);game.status=TournamentMatch.Status.FINISHED;game.winnerParticipantId=a.participantId();t.matches.add(game);t.bracket.add(new BracketNode("final",2,1,game.id,a.participantId(),b.participantId(),null,null,null,a.participantId(),"WIN"));t.status=TournamentStatus.FINISHED;
        var summary=service.playerSummary(t);assertThat(((RankingEntry)summary.get("champion")).participantId()).isEqualTo(a.participantId());assertThat((List<?>)summary.get("participants")).hasSize(2);assertThat((List<?>)summary.get("bracket")).hasSize(1);
        t.operations=t.operations.participant(a.participantId(),ParticipantStatus.DISQUALIFIED);service.changed(t);assertThat(service.playerSummary(t).get("champion")).isNull();
    }
    @Test void membershipIndexTracksCommittedChangesAndRemoval(){
        var t=cup(0);var user=UUID.randomUUID();assertThat(registry.relatedTo(t.ownerUserId)).contains(t);assertThat(registry.relatedTo(user)).isEmpty();
        t.registrations.add(new TournamentRegistration(UUID.randomUUID(),UUID.fromString(t.id),user,null,null,"Player",TournamentRegistration.Status.PENDING,Instant.now(),null,null));service.notifyChanged(t);assertThat(registry.relatedTo(user)).contains(t);
        t.registrations.clear();service.notifyChanged(t);assertThat(registry.relatedTo(user)).isEmpty();registry.remove(t.id);assertThat(registry.relatedTo(t.ownerUserId)).isEmpty();
    }
}
