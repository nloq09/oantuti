package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TournamentLifecycleTest {
    @Test void everyStatePairHasAnExplicitPolicyAndTerminalStatesCannotRestart() {
        Map<TournamentStatus,Set<TournamentStatus>> allowed=Map.of(
            TournamentStatus.DRAFT,Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CANCELLED),
            TournamentStatus.REGISTRATION,Set.of(TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.CANCELLED),
            TournamentStatus.CHECK_IN,Set.of(TournamentStatus.READY,TournamentStatus.CANCELLED),
            TournamentStatus.READY,Set.of(TournamentStatus.RUNNING,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.CANCELLED),
            TournamentStatus.RUNNING,Set.of(TournamentStatus.PAUSED,TournamentStatus.FINISHED,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.CANCELLED),
            TournamentStatus.PAUSED,Set.of(TournamentStatus.RUNNING,TournamentStatus.FINISHED,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.CANCELLED),
            TournamentStatus.RECOVERY_REQUIRED,Set.of(TournamentStatus.CANCELLED,TournamentStatus.FINISHED),
            TournamentStatus.FINISHED,Set.of(),TournamentStatus.CANCELLED,Set.of());
        for(var from:TournamentStatus.values())for(var to:TournamentStatus.values()) {
            var t=tournament();t.status=from;var before=t.statusChangedAt;
            if(allowed.get(from).contains(to)) {
                TournamentStateMachine.transition(t,to,Instant.parse("2026-10-14T01:00:00Z"));assertThat(t.status).isEqualTo(to);
            } else {
                assertThatThrownBy(()->TournamentStateMachine.transition(t,to,Instant.now())).hasMessage("INVALID_TOURNAMENT_TRANSITION");
                assertThat(t.status).isEqualTo(from);assertThat(t.statusChangedAt).isEqualTo(before);
            }
        }
    }
    @Test void phaseTimestampsDoNotResetMatchStartWhenPausedAndResumed() {
        var t=tournament();var time=Instant.parse("2026-10-14T01:00:00Z");
        for(var state:List.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY))TournamentStateMachine.transition(t,state,time);
        assertThat(t.startedAt).isNull();assertThat(t.finishedAt).isNull();
        TournamentStateMachine.transition(t,TournamentStatus.RUNNING,time);
        TournamentStateMachine.transition(t,TournamentStatus.PAUSED,time.plusSeconds(1));
        TournamentStateMachine.transition(t,TournamentStatus.RUNNING,time.plusSeconds(2));
        assertThat(t.startedAt).isEqualTo(time);assertThat(t.statusChangedAt).isEqualTo(time.plusSeconds(2));
        TournamentStateMachine.transition(t,TournamentStatus.FINISHED,time.plusSeconds(3));assertThat(t.finishedAt).isEqualTo(time.plusSeconds(3));
        assertThat(TournamentStateMachine.canTransition(null,TournamentStatus.DRAFT)).isFalse();
        assertThat(TournamentStateMachine.canTransition(TournamentStatus.DRAFT,null)).isFalse();
    }
    private Tournament tournament(){return new Tournament(UUID.randomUUID().toString(),"Lifecycle",TournamentFormat.ROUND_ROBIN,Instant.parse("2026-10-14T00:00:00Z"));}
}
