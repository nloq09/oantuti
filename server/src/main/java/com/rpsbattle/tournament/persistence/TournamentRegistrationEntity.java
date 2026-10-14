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
    @Column(name="bot_id",nullable=false) UUID botId;
    @Column(name="bot_version_id",nullable=false) UUID botVersionId;
    @Column(name="display_name",nullable=false,length=60) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) TournamentRegistration.Status status;
    @Column(name="registered_at",nullable=false) Instant registeredAt;
    @Column(name="approved_at") Instant approvedAt;
    @Column(name="checked_in_at") Instant checkedInAt;
    protected TournamentRegistrationEntity() {}
    void copy(TournamentRegistration r) {
        id=r.id();tournamentId=r.tournamentId();userId=r.userId();botId=r.botId();botVersionId=r.botVersionId();
        displayName=r.displayName();status=r.status();registeredAt=r.registeredAt();approvedAt=r.approvedAt();checkedInAt=r.checkedInAt();
    }
    TournamentRegistration domain() {return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,status,registeredAt,approvedAt,checkedInAt);}
}
