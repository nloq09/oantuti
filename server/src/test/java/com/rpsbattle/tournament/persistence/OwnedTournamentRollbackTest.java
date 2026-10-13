package com.rpsbattle.tournament.persistence;
import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.history.MatchPersistenceCoordinator;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.RankingService;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.scheduler.RoundRobinScheduler;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnedTournamentRollbackTest {
    @Test void failedStartCommitRestoresDraftAndDoesNotNotifySchedulerOrSpectators() {
        var registry=new TournamentRegistry();var artifacts=mock(BotUploadService.class);
        when(artifacts.authorize(anyString(),anyString())).thenAnswer(call->new BotUploadService.Upload(call.getArgument(0),"Bot",BotLanguage.PYTHON,"token"));when(artifacts.available(any())).thenReturn(true);
        var service=new TournamentService(registry,artifacts,new RoundRobinScheduler(),new RankingService(mock(com.rpsbattle.tournament.ranking.ScoringRule.class)));var repository=mock(TournamentRepository.class);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated outage")).when(repository).save(any());
        var coordinator=new TournamentPersistenceCoordinator(repository,registry,service,mock(MatchPersistenceCoordinator.class),artifacts);var accounts=mock(AccountAuthorizationService.class);
        var now=Instant.now();var user=new User(UserId.random(),"owner","owner@example.com","unused","Owner",UserRole.USER,AccountStatus.ACTIVE,now,now,null);when(accounts.requireUser(any())).thenReturn(user);
        var owned=new OwnedTournamentService(service,registry,coordinator,accounts,new TournamentAuthorizationService(accounts),mock(OwnedBotService.class));var tournament=service.create("Rollback",TournamentFormat.ROUND_ROBIN);tournament.ownerUserId=user.id().value();
        for(int i=0;i<2;i++) tournament.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"token","Bot "+i,42L,user.id().value()));
        var notified=new AtomicInteger();service.listen(t->notified.incrementAndGet());
        assertThatThrownBy(()->owned.start(com.rpsbattle.auth.AuthService.sessionAuthentication(user),tournament.id)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(tournament.status).isEqualTo(TournamentStatus.DRAFT);assertThat(tournament.matches).isEmpty();assertThat(tournament.startedAt).isNull();assertThat(tournament.revision).isZero();assertThat(notified.get()).isZero();assertThat(tournament.deferNotifications).isFalse();
    }
}
