package com.rpsbattle.tournament.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable application snapshot. Bot versions are immutable upload IDs until versioning is introduced. */
public record TournamentRegistration(UUID id, UUID tournamentId, UUID userId, UUID botId, UUID botVersionId,
        String displayName, Status status, Instant registeredAt, Instant approvedAt, Instant checkedInAt,String sourceHash,String artifactHash,Instant lockedAt) {
    public TournamentRegistration(UUID id,UUID tournamentId,UUID userId,UUID botId,UUID version,String name,Status status,Instant registered,Instant approved,Instant checked){this(id,tournamentId,userId,botId,version,name,status,registered,approved,checked,null,null,null);}
    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, CHECKED_IN, NOT_CHECKED_IN }
    @com.fasterxml.jackson.annotation.JsonProperty public String controllerType(){return botId==null?"HUMAN":"BOT";}
    public boolean active() {return status==Status.PENDING || status==Status.APPROVED || status==Status.CHECKED_IN;}
    public TournamentRegistration withStatus(Status next, Instant now) {
        return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,next,registeredAt,
            next==Status.APPROVED?now:approvedAt,next==Status.CHECKED_IN?now:checkedInAt,sourceHash,artifactHash,lockedAt);
    }
    public TournamentRegistration withLock(String hash,Instant now){return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,status,registeredAt,approvedAt,checkedInAt,hash,hash,lockedAt==null?now:lockedAt);}
    public TournamentRegistration withSourceHash(String hash){return new TournamentRegistration(id,tournamentId,userId,botId,botVersionId,displayName,status,registeredAt,approvedAt,checkedInAt,hash,artifactHash,lockedAt);}
}
