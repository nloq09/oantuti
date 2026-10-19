package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Claim protocol component for the future execution loop. Heartbeat alone never claims matches. */
public final class ClaimClient {
    private final CentralClient central;private final IdentityStore.Identity identity;
    public ClaimClient(CentralClient central,IdentityStore.Identity identity){this.central=central;this.identity=identity;}
    public record Request(UUID requestId,long epoch,int availableSlots,List<String> cachedArtifacts,String gameCoreVersion,String ruleSetVersion,int protocolVersion) {
        public Request {cachedArtifacts=List.copyOf(cachedArtifacts);}
    }
    public Request request(long epoch,int availableSlots,List<String> cache,String core,String rules){return new Request(UUID.randomUUID(),epoch,availableSlots,cache,core,rules,1);}
    public JsonNode claim(Request request)throws IOException,InterruptedException {
        var result=central.post(identity,"jobs/claim",request);var jobs=result.path("jobs");if(!jobs.isArray()||jobs.size()>Math.min(16,request.availableSlots()))throw new IOException("Invalid claim batch");
        Set<UUID> executions=new HashSet<>();Set<Integer> slots=new HashSet<>();
        try {for(var job:jobs){UUID execution=UUID.fromString(job.path("executionId").asText());UUID.fromString(job.path("jobId").asText());Instant.parse(job.path("leaseUntil").asText());int slot=job.path("slot").asInt();if(!job.path("workerId").asText().equals(identity.workerId().toString())||job.path("epoch").asLong()!=request.epoch()||slot<1||slot>256||!executions.add(execution)||!slots.add(slot)||!Set.of("CLAIMED","RUNNING","DECLINED","DONE","EXPIRED").contains(job.path("status").asText()))throw new IllegalArgumentException();}}
        catch(RuntimeException e){throw new IOException("Invalid execution assignment");}
        return result;
    }
    /** Only release work that has never started. Reuse executionId/reason when retrying. */
    public JsonNode decline(UUID job,UUID execution,String reason)throws IOException,InterruptedException {return central.post(identity,"jobs/"+job+"/decline",Map.of("executionId",execution,"reason",reason));}
}
