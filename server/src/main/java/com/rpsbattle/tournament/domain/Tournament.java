package com.rpsbattle.tournament.domain;
import java.time.Instant;
import java.util.*;
/** Mutation and consistent snapshots are guarded by the tournament monitor. */
public final class Tournament {
    public final String id, name;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public final String ownerToken = UUID.randomUUID().toString();
    public final TournamentFormat format;
    public UUID ownerUserId;
    @com.fasterxml.jackson.annotation.JsonIgnore public boolean deferNotifications;
    public TournamentStatus status = TournamentStatus.DRAFT;
    public final List<TournamentParticipant> participants = new ArrayList<>();
    public final List<TournamentMatch> matches = new ArrayList<>();
    public final Instant createdAt;
    public Instant startedAt, finishedAt;
    public long revision;
    public Tournament(String id, String name, TournamentFormat format, Instant createdAt) {
        this.id=id; this.name=name; this.format=format; this.createdAt=createdAt;
    }
}
