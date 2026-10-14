package com.rpsbattle.tournament.persistence;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="tournament_participants")
public class TournamentParticipantEntity {
    @Id UUID id;
    @Column(name="tournament_id",nullable=false) UUID tournamentId;
    @Column(name="bot_id",nullable=false) UUID botId;
    @Column(name="owner_user_id",nullable=false) UUID ownerUserId;
    @Column(name="display_name",nullable=false,length=60) String displayName;
    @Column(nullable=false) long seed;
    @Column(name="artifact_hash",length=64) String artifactHash;
    @Column(name="seed_number") Integer seedNumber;
    @Column(name="manual_seed") Integer manualSeed;
    protected TournamentParticipantEntity() {}
    TournamentParticipantEntity(UUID tournament,TournamentMetadata.Participant p) {id=p.id();tournamentId=tournament;botId=p.botId();ownerUserId=p.ownerUserId();displayName=p.displayName();seed=p.seed();artifactHash=p.artifactHash();seedNumber=p.seedNumber();manualSeed=p.manualSeed();}
    TournamentMetadata.Participant domain() {return new TournamentMetadata.Participant(id,botId,ownerUserId,displayName,seed,artifactHash,seedNumber,manualSeed);}
}
