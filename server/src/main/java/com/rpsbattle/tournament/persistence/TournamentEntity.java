package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tournaments")
public class TournamentEntity {
    @Id UUID id;
    @Column(name="owner_user_id",nullable=false) UUID ownerUserId;
    @Column(nullable=false,length=80) String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) TournamentFormat format;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) TournamentStatus status;
    @Column(nullable=false,length=16) String visibility="PUBLIC";
    @Column(nullable=false) long revision;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="started_at") Instant startedAt;
    @Column(name="finished_at") Instant finishedAt;
    @Column(name="status_changed_at",nullable=false) Instant statusChangedAt;
    @Column(name="check_in_required",nullable=false) boolean checkInRequired;
    @Column(name="max_participants",nullable=false) int maxParticipants;
    @Enumerated(EnumType.STRING) @Column(name="seeding_strategy",nullable=false,length=16) SeedingStrategy seedingStrategy;
    @Column(name="seeding_seed",nullable=false) long seedingSeed;
    protected TournamentEntity() {}
    void copy(TournamentMetadata t) {id=t.id();ownerUserId=t.ownerUserId();name=t.name();format=t.format();status=t.status();revision=t.revision();createdAt=t.createdAt();startedAt=t.startedAt();finishedAt=t.finishedAt();statusChangedAt=t.statusChangedAt();checkInRequired=t.checkInRequired();maxParticipants=t.maxParticipants();seedingStrategy=t.seedingStrategy();seedingSeed=t.seedingSeed();}
}
