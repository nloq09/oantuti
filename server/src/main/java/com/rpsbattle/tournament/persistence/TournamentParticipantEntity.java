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
    protected TournamentParticipantEntity() {}
    TournamentParticipantEntity(UUID tournament,TournamentMetadata.Participant p) {id=p.id();tournamentId=tournament;botId=p.botId();ownerUserId=p.ownerUserId();displayName=p.displayName();seed=p.seed();}
    TournamentMetadata.Participant domain() {return new TournamentMetadata.Participant(id,botId,ownerUserId,displayName,seed);}
}
