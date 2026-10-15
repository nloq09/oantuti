package com.rpsbattle.tournament.persistence;

import com.rpsbattle.tournament.domain.TournamentRegistration;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="tournament_registrations")
public class TournamentRegistrationEntity {
    @Id UUID id;
    @Column(name="tournament_id",nullable=false) UUID tournamentId;
    @Column(name="user_id",nullable=false) UUID userId;
    @Column(name="bot_id") UUID botId;
    @Column(name="bot_version_id") UUID botVersionId;
    @Column(name="human_user_id") UUID humanUserId;
    @Column(name="display_name",nullable=false,length=60) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) TournamentRegistration.Status status;
    @Column(name="registered_at",nullable=false) Instant registeredAt;
    @Column(name="approved_at") Instant approvedAt;
    @Column(name="checked_in_at") Instant checkedInAt;
    @Column(name="source_hash",length=64) String sourceHash;
    @Column(name="artifact_hash",length=64) String artifactHash;
    @Column(name="locked_at") Instant lockedAt;
    protected TournamentRegistrationEntity() {}
    void copy(TournamentRegistration r) {
        id=r.id();tournamentId=r.tournamentId();userId=r.userId();botId=r.botId();botVersionId=r.botVersionId();humanUserId=botId==null?userId:null;
        displayName=r.displayName();status=r.status();registeredAt=r.registeredAt();approvedAt=r.approvedAt();checkedInAt=r.checkedInAt();sourceHash=r.sourceHash();artifactHash=r.artifactHash();lockedAt=r.lockedAt();
    }
    TournamentRegistration domain() {return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,status,registeredAt,approvedAt,checkedInAt,sourceHash,artifactHash,lockedAt);}
}
