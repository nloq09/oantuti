package com.rpsbattle.bot.ownership;

import com.rpsbattle.bot.upload.BotLanguage;
import java.time.Instant;
import java.util.UUID;

/** Application metadata; artifact paths never belong in public responses. */
public record BotMetadata(UUID id, UUID ownerUserId, String name, BotLanguage language,
        String sourcePath, Status status, Instant createdAt, Instant updatedAt) {
    public enum Status { UPLOADED, READY, BUILD_ERROR, DELETED }
    public BotMetadata rename(String name) {
        return new BotMetadata(id,ownerUserId,name,language,sourcePath,status,createdAt,Instant.now());
    }
    public BotMetadata deleted() {
        return new BotMetadata(id,ownerUserId,name,language,sourcePath,Status.DELETED,createdAt,Instant.now());
    }
}
