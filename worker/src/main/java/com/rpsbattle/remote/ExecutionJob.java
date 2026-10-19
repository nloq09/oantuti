package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Strict allowlist: jobs never supply a host command, path, image or executable option. */
public record ExecutionJob(UUID jobId, UUID executionId, UUID workerId, long epoch, int slot,
                           Instant leaseUntil, Spec match) {
    public record Spec(UUID matchId, UUID tournamentId, ArtifactRef botA, ArtifactRef botB,
                       String gameCoreVersion, String ruleSetVersion, long seed, int turnTimeoutMs,
                       int matchHardTimeoutMs, int maxTurns, String minimumTrust) {}
    public static ExecutionJob parse(JsonNode node, UUID worker, long epoch) throws IOException {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var spec = mapper.treeToValue(node.path("match"), Spec.class);
            var job = new ExecutionJob(UUID.fromString(node.path("jobId").asText()), UUID.fromString(node.path("executionId").asText()),
                    UUID.fromString(node.path("workerId").asText()), node.path("epoch").asLong(), node.path("slot").asInt(),
                    Instant.parse(node.path("leaseUntil").asText()), spec);
            if (!"CLAIMED".equals(node.path("status").asText()) || !job.workerId.equals(worker) || job.epoch != epoch || job.slot < 1 || job.slot > 256
                    || spec.matchId == null || spec.tournamentId == null || spec.botA == null || spec.botB == null
                    || !"1".equals(spec.gameCoreVersion) || !"classic-online-v1".equals(spec.ruleSetVersion)
                    || spec.turnTimeoutMs < 1 || spec.turnTimeoutMs > 10000 || spec.matchHardTimeoutMs < spec.turnTimeoutMs
                    || spec.matchHardTimeoutMs > 3600000 || spec.maxTurns < 1 || spec.maxTurns > 10000
                    || !Set.of("TRUSTED", "COMMUNITY").contains(spec.minimumTrust)
                    || !Instant.now().plusMillis(spec.matchHardTimeoutMs).isBefore(job.leaseUntil)) throw new IllegalArgumentException();
            return job;
        } catch (Exception e) { throw new IOException("Invalid or incompatible execution job"); }
    }
}
