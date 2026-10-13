package com.rpsbattle.replay;
import java.time.Instant;
import java.util.UUID;
public record ReplayMetadata(UUID id, String matchId, String filePath, int eventCount,
        boolean valid, Instant createdAt, String sha256) {}
