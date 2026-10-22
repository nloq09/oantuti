package com.rpsbattle.tournament.timeline;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Tracks optional early closure and legacy explicit roster locks; scheduled coding needs neither. */
@Entity @Table(name="tournament_registration_controls")
public class RegistrationControl {
    @Id @Column(name="tournament_id") UUID tournamentId;
    @Column(name="closed_at") Instant closedAt;
    @Column(name="locked_at") Instant lockedAt;
    @Column(name="locked_by") UUID lockedBy;
    @Column(name="locked_by_name",length=80) String lockedByName;
    @Column(name="locked_count") Integer lockedCount;
    protected RegistrationControl() {}
}
