package com.rpsbattle.tournament;
import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.history.MatchPersistenceCoordinator;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.registry.*;
import com.rpsbattle.tournament.scheduler.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentAdministrationTest {
    @Test void swissResultCannotInvalidateAlreadyPairedNextRound(){var t=cup(4,TournamentFormat.SWISS);for(var m:t.matches)TournamentFixtures.finish(m,m.participantA.participantId(),8,1);assertThat(com.rpsbattle.tournament.scheduler.SwissScheduler.advance(t)).isTrue();var m=t.matches.getFirst();admin.review(auth,t.id,t.revision,m.id,"Check previous Swiss round",false);assertThatThrownBy(()->admin.override(auth,t.id,t.revision,m.id,m.participantB.participantId(),"Would invalidate next pairing")).hasMessage("DOWNSTREAM_MATCH_STARTED");assertThat(t.swiss.currentRound()).isEqualTo(2);}
    @Test void groupQualificationCannotBeRewrittenAfterTheCrossBracketIsFrozen(){
        var t=cup(4,TournamentFormat.GROUP_KNOCKOUT);for(var m:t.matches)TournamentFixtures.finish(m,m.participantA.participantId(),8,1);
        assertThat(com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.qualify(t)).isTrue();var group=t.matches.getFirst();var qualifiers=t.groupStage;
        admin.review(auth,t.id,t.revision,group.id,"Check group result",false);
        assertThatThrownBy(()->admin.override(auth,t.id,t.revision,group.id,group.participantB.participantId(),"Would change qualification")).hasMessage("GROUP_QUALIFICATION_LOCKED");
        assertThat(t.groupStage).isEqualTo(qualifiers);assertThat(t.operations.reviews()).containsKey(group.id);
    }
    @Test void reviewAssignmentRejectionAndDisqualificationKeepFactsAndCloseReviewAtomically(){
        var t=cup(3,TournamentFormat.ROUND_ROBIN);var m=t.matches.getFirst();TournamentFixtures.finish(m,m.participantA.participantId(),8,2);String original=m.winnerParticipantId;
        admin.review(auth,t.id,t.revision,m.id,"Check result",false);admin.assignReview(auth,t.id,t.revision,m.id,"Investigating");
        assertThat(t.operations.audit().getLast().type()).isEqualTo("MATCH_REVIEW_ASSIGNED");
        admin.rejectReview(auth,t.id,t.revision,m.id,"Request unsupported");assertThat(t.operations.reviews()).isEmpty();assertThat(m.winnerParticipantId).isEqualTo(original);
        admin.review(auth,t.id,t.revision,m.id,"Second review",false);admin.disqualifyReview(auth,t.id,t.revision,m.id,m.participantB.participantId(),"Disqualify unsafe entry");
        assertThat(t.operations.reviews()).isEmpty();assertThat(t.operations.participants().get(m.participantB.participantId())).isEqualTo(ParticipantStatus.DISQUALIFIED);assertThat(m.winnerParticipantId).isEqualTo(original);
        assertThat(t.operations.audit().getLast().type()).isEqualTo("MATCH_REVIEW_CLOSED");
    }
    final TournamentRegistry registry=new TournamentRegistry();
    final BotUploadService artifacts=mock(BotUploadService.class);
    final TournamentRepository repository=mock(TournamentRepository.class);
    final RankingService rankings=new RankingService(new ClassicScoringRule());
    final TournamentService service=new TournamentService(registry,artifacts,new FormatTournamentScheduler(),rankings);
    final TournamentPersistenceCoordinator persistence=new TournamentPersistenceCoordinator(repository,registry,service,mock(MatchPersistenceCoordinator.class),artifacts);
    final AccountAuthorizationService accounts=mock(AccountAuthorizationService.class);
    final User user=new User(UserId.random(),"owner","owner@example.com","unused","Owner",UserRole.USER,AccountStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH,null);
    final org.springframework.security.core.Authentication auth=AuthService.sessionAuthentication(user);
    final OwnedTournamentService owned=new OwnedTournamentService(service,registry,persistence,accounts,new TournamentAuthorizationService(accounts),mock(OwnedBotService.class));
    final TournamentAdministrationService admin=new TournamentAdministrationService(owned,service);
    TournamentAdministrationTest(){when(accounts.requireUser(any())).thenReturn(user);}
    Tournament cup(int n,TournamentFormat format){var t=registry.create("Managed",format);t.ownerUserId=user.id().value();for(int i=0;i<n;i++)t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"secret","Bot "+i,i,user.id().value(),null,i+1,null));t.matches.addAll(new FormatTournamentScheduler().schedule(t));t.status=TournamentStatus.RUNNING;t.startedAt=Instant.now();return t;}
    @Test void seriesOverrideCannotRewriteStartedSubgamesOrReopenFinishedCupImplicitly(){
        var t=cup(2,TournamentFormat.SINGLE_ELIMINATION);t.bestOf=3;var first=t.matches.getFirst();TournamentFixtures.finish(first,first.participantA.participantId(),8,2);TournamentSeries.advance(t);var last=t.matches.getLast();TournamentFixtures.finish(last,first.participantA.participantId(),8,2);t.matches.addAll(SingleEliminationScheduler.advance(t));t.status=TournamentStatus.FINISHED;
        admin.review(auth,t.id,t.revision,last.id,"Review series",false);assertThatThrownBy(()->admin.override(auth,t.id,t.revision,last.id,last.participantB.participantId(),"Would require game three")).hasMessage("SERIES_REOPEN_REQUIRED");assertThat(last.winnerParticipantId).isEqualTo(first.participantA.participantId());assertThat(t.bracket.getLast().winnerParticipantId()).isEqualTo(first.participantA.participantId());
        t.status=TournamentStatus.PAUSED;admin.review(auth,t.id,t.revision,first.id,"Review earlier game",false);assertThatThrownBy(()->admin.override(auth,t.id,t.revision,first.id,first.participantB.participantId(),"Downstream game already exists")).hasMessage("DOWNSTREAM_MATCH_STARTED");
    }
    @Test void managerCannotFinishPausedRoundRobinBeforeTheSeriesIsDecided(){var t=cup(2,TournamentFormat.ROUND_ROBIN);t.bestOf=3;var first=t.matches.getFirst();TournamentFixtures.finish(first,first.participantA.participantId(),8,2);t.status=TournamentStatus.PAUSED;assertThatThrownBy(()->admin.control(auth,t.id,t.revision,"finish","Cannot finish after only one game")).hasMessage("SERIES_NOT_FINISHED");assertThat(t.status).isEqualTo(TournamentStatus.PAUSED);}
    @Test void pauseResumeAndManualRoundsAreAuthorizedAuditedAndRejectStaleRevision(){
        var t=cup(4,TournamentFormat.ROUND_ROBIN);admin.control(auth,t.id,t.revision,"pause",null);assertThat(t.status).isEqualTo(TournamentStatus.PAUSED);
        assertThatThrownBy(()->admin.control(auth,t.id,0L,"resume",null)).hasMessage("STALE_TOURNAMENT_REVISION");
        admin.control(auth,t.id,t.revision,"resume",null);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);
        t.operations=t.operations.rounds(false,1);assertThatThrownBy(()->admin.control(auth,t.id,t.revision,"next-round",null)).hasMessage("ROUND_NOT_FINISHED");
        t.matches.stream().filter(m->m.round==1).forEach(m->TournamentFixtures.finish(m,m.participantA.participantId(),10,0));admin.control(auth,t.id,t.revision,"next-round",null);assertThat(t.operations.releasedRound()).isEqualTo(2);
        assertThat(t.operations.audit()).allMatch(e->e.actor().equals(user.id().value()));assertThat(t.operations.audit()).hasSize(3);
    }
    @Test void disqualificationSettlesFutureRoundRobinGamesWithoutDeletingHistory(){
        var t=cup(5,TournamentFormat.ROUND_ROBIN);t.status=TournamentStatus.PAUSED;String id=t.participants.getFirst().participantId();
        admin.participant(auth,t.id,t.revision,id,ParticipantStatus.DISQUALIFIED,"illegal bot");
        assertThat(t.participants).hasSize(5);assertThat(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED)).hasSize(4);
        assertThat(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED)).allMatch(m->!id.equals(m.winnerParticipantId)&&m.historyMatchId==null&&m.decisionReason.equals("FORFEIT"));
        var row=rankings.rank(t).stream().filter(r->r.participantId().equals(id)).findFirst().orElseThrow();assertThat(row.losses()).isEqualTo(4);assertThat(row.points()).isZero();
    }
    @Test void knockoutForfeitPromotesOpponentAndDoubleForfeitAwardsNoDrawPoint(){
        var t=cup(6,TournamentFormat.SINGLE_ELIMINATION);t.status=TournamentStatus.PAUSED;String id=t.participants.getFirst().participantId();admin.participant(auth,t.id,t.revision,id,ParticipantStatus.WITHDRAWN,"withdraw");
        for(int i=0;i<5&&!SingleEliminationScheduler.complete(t);i++){TournamentAdministrationService.settleForfeits(t);for(var m:t.matches)if(m.status==TournamentMatch.Status.PENDING)TournamentFixtures.finish(m,m.participantB.participantId(),0,9);t.matches.addAll(SingleEliminationScheduler.advance(t));}
        assertThat(SingleEliminationScheduler.complete(t)).isTrue();assertThat(t.bracket.getLast().winnerParticipantId()).isNotEqualTo(id);
        var rr=cup(2,TournamentFormat.ROUND_ROBIN);for(var p:rr.participants)rr.operations=rr.operations.participant(p.participantId(),ParticipantStatus.DISQUALIFIED);TournamentAdministrationService.settleForfeits(rr);
        assertThat(rankings.rank(rr)).allMatch(r->r.losses()==1&&r.points()==0&&r.draws()==0);
    }
    @Test void reviewOverrideRecordsOldAndNewResultsAndCannotRewriteStartedDownstream(){
        var t=cup(4,TournamentFormat.SINGLE_ELIMINATION);for(var m:t.matches)TournamentFixtures.finish(m,m.participantA.participantId(),9,0);t.matches.addAll(SingleEliminationScheduler.advance(t));
        var first=t.matches.getFirst();String old=first.winnerParticipantId;admin.review(auth,t.id,t.revision,first.id,"verify winner",false);
        admin.override(auth,t.id,t.revision,first.id,first.participantB.participantId(),"correct adjudication");assertThat(first.winnerParticipantId).isNotEqualTo(old);assertThat(t.operations.reviews()).isEmpty();
        var event=t.operations.audit().getLast();assertThat(event.type()).isEqualTo("RESULT_OVERRIDDEN");assertThat(event.beforeValue()).contains(old);assertThat(event.afterValue()).contains(first.participantB.participantId());assertThat(event.reason()).isEqualTo("correct adjudication");
        t.matches.getLast().status=TournamentMatch.Status.RUNNING;admin.review(auth,t.id,t.revision,first.id,"second review",false);String current=first.winnerParticipantId;
        assertThatThrownBy(()->admin.override(auth,t.id,t.revision,first.id,old,"blocked")).hasMessage("DOWNSTREAM_MATCH_STARTED");assertThat(first.winnerParticipantId).isEqualTo(current);
    }
    @Test void retryOnlyInfrastructureErrorsClearsRoomAndPreservesAttemptAudit(){
        var t=cup(2,TournamentFormat.ROUND_ROBIN);t.status=TournamentStatus.RECOVERY_REQUIRED;var m=t.matches.getFirst();m.status=TournamentMatch.Status.FAILED;m.failure="SERVER_RESTART";m.historyMatchId="old-history";m.onlineMatchId="old-room";
        admin.retry(auth,t.id,t.revision,m.id,"infrastructure restored");assertThat(m.status).isEqualTo(TournamentMatch.Status.PENDING);assertThat(m.attempt).isEqualTo(2);assertThat(m.historyMatchId).isNull();assertThat(t.operations.audit().getLast().beforeValue()).contains("old-history");
        admin.control(auth,t.id,t.revision,"resume",null);assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);
        TournamentFixtures.finish(m,m.participantA.participantId(),10,0);t.status=TournamentStatus.PAUSED;assertThatThrownBy(()->admin.retry(auth,t.id,t.revision,m.id,"not allowed")).hasMessage("MATCH_NOT_RETRYABLE");
    }
    @Test void failedAdministrativeCommitRestoresMatchAndAuditInPlace(){
        var t=cup(2,TournamentFormat.ROUND_ROBIN);var m=t.matches.getFirst();TournamentFixtures.finish(m,m.participantA.participantId(),8,0);t.status=TournamentStatus.PAUSED;
        admin.review(auth,t.id,t.revision,m.id,"review",false);var snapshot=TournamentDetails.snapshot(t);var original=m.winnerParticipantId;
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("outage")).when(repository).save(any());
        assertThatThrownBy(()->admin.override(auth,t.id,t.revision,m.id,m.participantB.participantId(),"override")).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(t.matches.getFirst()).isSameAs(m);assertThat(m.winnerParticipantId).isEqualTo(original);assertThat(TournamentDetails.snapshot(t)).isEqualTo(snapshot);
    }
}
