package com.rpsbattle.tournament.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable application snapshot. Bot versions are immutable upload IDs until versioning is introduced. */
public record TournamentRegistration(UUID id, UUID tournamentId, UUID userId, UUID botId, UUID botVersionId,
        String displayName, Status status, Instant registeredAt, Instant approvedAt, Instant checkedInAt) {
    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, CHECKED_IN, NOT_CHECKED_IN }
    public boolean active() {return status==Status.PENDING || status==Status.APPROVED || status==Status.CHECKED_IN;}
    public TournamentRegistration withStatus(Status next, Instant now) {
        return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,next,registeredAt,
            next==Status.APPROVED?now:approvedAt,next==Status.CHECKED_IN?now:checkedInAt);
    }
}
