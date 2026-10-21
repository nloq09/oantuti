package com.rpsbattle.tournament;
import com.rpsbattle.tournament.cleanup.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TournamentCleanupPolicyTest {
 final Instant now=Instant.parse("2026-10-21T00:00:00Z");
 Tournament old(){var t=new Tournament(UUID.randomUUID().toString(),"Giải có tên bình thường",TournamentFormat.ROUND_ROBIN,now.minus(Duration.ofDays(40)));t.ownerUserId=UUID.randomUUID();return t;}
 @Test void onlyEmptyIdleDraftsAndCancelledAreCandidates(){var t=old();assertThat(TournamentCleanupPolicy.reason(t,now)).contains("EMPTY_ABANDONED_DRAFT");t.status=TournamentStatus.CANCELLED;assertThat(TournamentCleanupPolicy.reason(t,now)).contains("EMPTY_CANCELLED");for(var status:TournamentStatus.values())if(status!=TournamentStatus.DRAFT&&status!=TournamentStatus.CANCELLED){t.status=status;assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();}}
 @Test void ageThresholdAndRealActivityAreRequired(){var t=old();assertThat(TournamentCleanupPolicy.reason(t,t.createdAt.plus(Duration.ofDays(14)).minusSeconds(1))).isEmpty();assertThat(TournamentCleanupPolicy.reason(t,t.createdAt.plus(Duration.ofDays(14)))).isPresent();TournamentAudit.append(t,"TIMING_CONFIGURED",t.ownerUserId,t.id,null,null,null);assertThat(TournamentCleanupPolicy.reason(t,Instant.now())).isEmpty();}
 @Test void futureSchedulesAndStartedGamesAreProtected(){var t=old();t.timing=new TournamentTiming(null,null,now.plusSeconds(1));assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();t.timing=TournamentTiming.manual();t.botPolicy=new TournamentBotPolicy(null,null,null,now.plusSeconds(86400),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();t.botPolicy=TournamentBotPolicy.defaults();t.startedAt=t.createdAt;assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();}
 @Test void rosterTeamsAndRegistrationsProtectEvenCancelledTournaments(){var t=old();t.status=TournamentStatus.CANCELLED;t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),null,null,"Player",1,t.ownerUserId));assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();t.participants.clear();t.teams=new TeamState(List.of(new TeamState.Team("team","Team",null,t.ownerUserId,List.of())),List.of());assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();}
 @Test void unmanagedAndInFlightAndAlreadyArchivedAreNeverCandidates(){var t=old();t.archived=true;assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();t.archived=false;t.deferNotifications=true;assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();t.deferNotifications=false;t.ownerUserId=null;assertThat(TournamentCleanupPolicy.reason(t,now)).isEmpty();}
 @Test void completedAndCancelledHistoryDoesNotConsumeActiveCapacity(){var registry=new TournamentRegistry();for(int i=0;i<120;i++)registry.create("History",TournamentFormat.ROUND_ROBIN).status=i%2==0?TournamentStatus.FINISHED:TournamentStatus.CANCELLED;for(int i=0;i<100;i++)registry.create("Active",TournamentFormat.ROUND_ROBIN);assertThatThrownBy(()->registry.create("Excess",TournamentFormat.ROUND_ROBIN)).hasMessage("TOURNAMENT_LIMIT");}
}
