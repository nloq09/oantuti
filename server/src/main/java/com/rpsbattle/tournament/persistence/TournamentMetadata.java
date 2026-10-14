package com.rpsbattle.tournament.persistence;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.*;

/** Persistent data snapshot; capabilities remain exclusively in artifact storage. */
public record TournamentMetadata(UUID id,UUID ownerUserId,String name,TournamentFormat format,TournamentStatus status,
        long revision,Instant createdAt,Instant startedAt,Instant finishedAt,List<Participant> participants,List<Entry> matches,Instant statusChangedAt,boolean checkInRequired,int maxParticipants,List<TournamentRegistration> registrations,SeedingStrategy seedingStrategy,long seedingSeed,TournamentDetails details) {
    public TournamentMetadata(UUID id,UUID owner,String name,TournamentFormat format,TournamentStatus status,long revision,Instant created,Instant started,Instant finished,List<Participant> participants,List<Entry> matches,Instant changed,boolean checkIn,int capacity,List<TournamentRegistration> registrations,SeedingStrategy strategy,long seed) {this(id,owner,name,format,status,revision,created,started,finished,participants,matches,changed,checkIn,capacity,registrations,strategy,seed,null);}
    public TournamentMetadata {participants=List.copyOf(participants);matches=List.copyOf(matches);registrations=List.copyOf(registrations);}
    public TournamentMetadata(UUID id,UUID owner,String name,TournamentFormat format,TournamentStatus status,long revision,Instant created,Instant started,Instant finished,List<Participant> participants,List<Entry> matches) {
        this(id,owner,name,format,status,revision,created,started,finished,participants,matches,finished!=null?finished:started!=null?started:created);
    }
    public TournamentMetadata(UUID id,UUID owner,String name,TournamentFormat format,TournamentStatus status,long revision,Instant created,Instant started,Instant finished,List<Participant> participants,List<Entry> matches,Instant changed) {
        this(id,owner,name,format,status,revision,created,started,finished,participants,matches,changed,false,10,List.of());
    }
    public TournamentMetadata(UUID id,UUID owner,String name,TournamentFormat format,TournamentStatus status,long revision,Instant created,Instant started,Instant finished,List<Participant> participants,List<Entry> matches,Instant changed,boolean checkInRequired,int maxParticipants,List<TournamentRegistration> registrations){this(id,owner,name,format,status,revision,created,started,finished,participants,matches,changed,checkInRequired,maxParticipants,registrations,SeedingStrategy.RANDOM,0);}
    public record Participant(UUID id,UUID botId,UUID ownerUserId,String displayName,long seed,String artifactHash,Integer seedNumber,Integer manualSeed) {public Participant(UUID id,UUID bot,UUID owner,String name,long seed){this(id,bot,owner,name,seed,null,null,null);}}
    public record Entry(UUID id,UUID a,UUID b,String historyId,String roomId,TournamentMatch.Status status,
        UUID winner,String result,String failure,int remainingA,int remainingB,Instant startedAt,Instant finishedAt,MatchMetadata history) {}
}
