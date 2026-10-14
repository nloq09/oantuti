package com.rpsbattle.tournament.domain;

import com.rpsbattle.online.DemoRequestException;
import java.time.Instant;
import java.util.*;

/** Tournament lifecycle only; execution/eligibility guards belong to the application service. */
public final class TournamentStateMachine {
    private static final Map<TournamentStatus,Set<TournamentStatus>> NEXT=Map.of(
        TournamentStatus.DRAFT,Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CANCELLED),
        TournamentStatus.REGISTRATION,Set.of(TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.CANCELLED),
        TournamentStatus.CHECK_IN,Set.of(TournamentStatus.READY,TournamentStatus.CANCELLED),
        TournamentStatus.READY,Set.of(TournamentStatus.RUNNING,TournamentStatus.CANCELLED,TournamentStatus.RECOVERY_REQUIRED),
        TournamentStatus.RUNNING,Set.of(TournamentStatus.PAUSED,TournamentStatus.FINISHED,TournamentStatus.CANCELLED,TournamentStatus.RECOVERY_REQUIRED),
        TournamentStatus.PAUSED,Set.of(TournamentStatus.RUNNING,TournamentStatus.FINISHED,TournamentStatus.CANCELLED,TournamentStatus.RECOVERY_REQUIRED),
        TournamentStatus.RECOVERY_REQUIRED,Set.of(TournamentStatus.CANCELLED,TournamentStatus.FINISHED),
        TournamentStatus.FINISHED,Set.of(),TournamentStatus.CANCELLED,Set.of());
    private TournamentStateMachine() {}
    public static Set<TournamentStatus> next(TournamentStatus from){return NEXT.getOrDefault(from,Set.of());}
    public static boolean canTransition(TournamentStatus from,TournamentStatus to){return from!=null && to!=null && next(from).contains(to);}
    public static void transition(Tournament tournament,TournamentStatus target,Instant now) {
        synchronized(tournament) {
            if(now==null || !canTransition(tournament.status,target))throw new DemoRequestException("INVALID_TOURNAMENT_TRANSITION");
            var previous=tournament.status;tournament.status=target;tournament.statusChangedAt=now;
            if(previous==TournamentStatus.READY && target==TournamentStatus.RUNNING && tournament.startedAt==null)tournament.startedAt=now;
            if(target==TournamentStatus.FINISHED || target==TournamentStatus.CANCELLED)tournament.finishedAt=now;
        }
    }
}
