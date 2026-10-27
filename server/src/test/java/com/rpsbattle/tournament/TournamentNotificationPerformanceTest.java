package com.rpsbattle.tournament;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.auth.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.timeline.TimelineRegistrationStore;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentNotificationPerformanceTest {
    final TournamentRegistry registry=spy(new TournamentRegistry());
    final AccountAuthorizationService accounts=mock(AccountAuthorizationService.class);
    final TimelineRegistrationStore applications=mock(TimelineRegistrationStore.class);
    final TournamentNotificationService notifications=new TournamentNotificationService(accounts,mock(TournamentService.class),mock(OwnedTournamentService.class),registry);
    final User actor=new User(UserId.random(),"reader","reader@example.test","hash","Reader",UserRole.USER,AccountStatus.ACTIVE,Instant.now(),Instant.now(),null,0);
    final org.springframework.security.core.Authentication auth=AuthService.sessionAuthentication(actor);
    TournamentNotificationPerformanceTest(){notifications.applications=applications;when(accounts.requireUser(auth)).thenReturn(actor);when(applications.notificationMemberships(actor.id().value())).thenReturn(Map.of());}
    Tournament cup(UUID owner,boolean timeline){var t=new Tournament(UUID.randomUUID().toString(),"Notifications",TournamentFormat.SINGLE_ELIMINATION,Instant.now());t.ownerUserId=owner;if(timeline){var now=Instant.now();t.timeline=new TournamentTimeline(now,now.plusSeconds(1),now.plusSeconds(2),now.plusSeconds(3),now.plusSeconds(4),now.plusSeconds(5),now.plusSeconds(6),now.plusSeconds(7),now.plusSeconds(8),now.plusSeconds(9),null);}registry.register(t);return t;}
    @Test void unchangedReadsReuseBoundedProjectionButUnreadCountIncludesAllHistory(){
        var t=cup(actor.id().value(),false);var audit=spy(new ArrayList<TournamentAuditEvent>());for(int i=1;i<=75;i++)audit.add(new TournamentAuditEvent(UUID.randomUUID(),i,"TOURNAMENT_STARTED",null,t.id,null,null,null,Instant.EPOCH.plusSeconds(i)));
        t.operations=mock(TournamentOperations.class);when(t.operations.audit()).thenReturn(audit);clearInvocations(audit);
        var first=notifications.feed(auth);var second=notifications.feed(auth);assertThat((List<?>)first.get("items")).hasSize(50);assertThat(second.get("unread")).isEqualTo(75L);verify(audit,times(1)).iterator();
        t.notificationReads.put(actor.id().value().toString(),60L);assertThat(notifications.feed(auth).get("unread")).isEqualTo(15L);verify(audit,times(2)).iterator();
        audit.add(new TournamentAuditEvent(UUID.randomUUID(),76,"TOURNAMENT_STARTED",null,t.id,null,null,null,Instant.EPOCH.plusSeconds(76)));assertThat(notifications.feed(auth).get("unread")).isEqualTo(16L);verify(audit,times(3)).iterator();
    }
    @Test void feedSelectsRelatedTournamentsWithOneMembershipReadAndNeverScansAllTournaments(){
        Tournament mine=null;for(int i=0;i<100;i++){var t=cup(UUID.randomUUID(),true);TournamentAudit.append(t,"TIMELINE_CODING_STARTED",null,t.id,null,null,null);if(i==73)mine=t;}
        when(applications.notificationMemberships(actor.id().value())).thenReturn(Map.of(UUID.fromString(mine.id),new TimelineRegistrationStore.NotificationMembership(UUID.randomUUID(),"APPROVED")));
        var feed=notifications.feed(auth);assertThat((List<?>)feed.get("items")).hasSize(1);assertThat(feed.get("through").toString()).contains(mine.id);verify(applications,times(1)).notificationMemberships(actor.id().value());verify(applications,never()).notificationMembership(any(),any());verify(applications,never()).mine(any(),any());verify(registry,never()).findAll();
    }
    @Test void membershipChangeDuringBatchIsRecheckedAndDisabledAccountsCannotUseCachedFeed(){
        var t=cup(UUID.randomUUID(),true);TournamentAudit.append(t,"TIMELINE_CODING_STARTED",null,t.id,null,null,null);var member=UUID.randomUUID();
        when(applications.notificationMemberships(actor.id().value())).thenAnswer(call->{t.revision++;registry.updateMemberships(t);return Map.of(UUID.fromString(t.id),new TimelineRegistrationStore.NotificationMembership(member,"APPROVED"));});
        when(applications.notificationMembership(UUID.fromString(t.id),actor.id().value())).thenReturn(new TimelineRegistrationStore.NotificationMembership(member,"REJECTED"));
        assertThat((List<?>)notifications.feed(auth).get("items")).isEmpty();verify(applications).notificationMembership(UUID.fromString(t.id),actor.id().value());
        when(accounts.requireUser(auth)).thenThrow(new AuthException("ACCOUNT_DISABLED",403));assertThatThrownBy(()->notifications.feed(auth)).hasMessage("ACCOUNT_DISABLED");verify(applications,times(1)).notificationMemberships(any());
    }
    @Test void removedLegacyRegistrationDoesNotReadPreviouslyCachedTournament(){
        var t=cup(UUID.randomUUID(),false);t.registrations.add(new TournamentRegistration(UUID.randomUUID(),UUID.fromString(t.id),actor.id().value(),null,null,"Reader",TournamentRegistration.Status.PENDING,Instant.now(),null,null));registry.updateMemberships(t);TournamentAudit.append(t,"TOURNAMENT_STARTED",null,t.id,null,null,null);
        assertThat((List<?>)notifications.feed(auth).get("items")).hasSize(1);t.registrations.clear();registry.updateMemberships(t);assertThat((List<?>)notifications.feed(auth).get("items")).isEmpty();
    }
}
