package com.rpsbattle.tournament.persistence;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.*;

/** Persistent data snapshot; capabilities remain exclusively in artifact storage. */
public record TournamentMetadata(UUID id,UUID ownerUserId,String name,TournamentFormat format,TournamentStatus status,
        long revision,Instant createdAt,Instant startedAt,Instant finishedAt,List<Participant> participants,List<Entry> matches) {
    public TournamentMetadata {participants=List.copyOf(participants);matches=List.copyOf(matches);}
    public record Participant(UUID id,UUID botId,UUID ownerUserId,String displayName,long seed) {}
    public record Entry(UUID id,UUID a,UUID b,String historyId,String roomId,TournamentMatch.Status status,
        UUID winner,String result,String failure,int remainingA,int remainingB,Instant startedAt,Instant finishedAt,MatchMetadata history) {}
}
