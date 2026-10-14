package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.TournamentAuditEvent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tournament_audit_events")
public class TournamentAuditEntity {
    @Id UUID id;
    @Column(name="tournament_id",nullable=false) UUID tournamentId;
    @Column(name="sequence_number",nullable=false) long sequence;
    @Column(name="event_type",nullable=false,length=64) String type;
    @Column(name="actor_user_id") UUID actor;
    @Column(name="target_id",length=160) String target;
    @Column(length=500) String reason;
    @Column(name="before_value",columnDefinition="text") String before;
    @Column(name="after_value",columnDefinition="text") String after;
    @Column(name="occurred_at",nullable=false) Instant at;
    protected TournamentAuditEntity(){}
    TournamentAuditEntity(UUID tournamentId,TournamentAuditEvent e){this.tournamentId=tournamentId;id=e.id();sequence=e.sequence();type=e.type();actor=e.actor();target=e.target();reason=e.reason();before=e.beforeValue();after=e.afterValue();at=e.at();}
}
