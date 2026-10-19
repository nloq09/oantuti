package com.rpsbattle.remote;
import java.io.*;
import java.time.Instant;
import java.util.*;

public final class ArtifactClient {
    private final CentralClient central;private final IdentityStore.Identity identity;private final ArtifactCache cache;
    public ArtifactClient(CentralClient central,IdentityStore.Identity identity,ArtifactCache cache){this.central=central;this.identity=identity;this.cache=cache;}
    public ArtifactCache.Handle acquire(UUID execution,ArtifactRef ref)throws IOException,InterruptedException {return cache.acquire(ref,()->central.artifact(identity,execution,ref));}
    public int prefetchAssigned()throws IOException,InterruptedException {
        var jobs=central.assigned(identity).path("jobs");if(!jobs.isArray()||jobs.size()>256)throw new IOException("Invalid assignment list");int count=0;
        for(var job:jobs){
            UUID execution;try {execution=UUID.fromString(job.path("executionId").asText());if(!job.path("workerId").asText().equals(identity.workerId().toString())||!Set.of("CLAIMED","RUNNING").contains(job.path("status").asText())||!Instant.parse(job.path("leaseUntil").asText()).isAfter(Instant.now()))throw new IllegalArgumentException();}catch(RuntimeException e){throw new IOException("Invalid or expired assignment");}
            var handles=new ArrayList<ArtifactCache.Handle>();try{for(String side:List.of("botA","botB")){var node=job.path("match").path(side);ArtifactRef ref;try{ref=new ArtifactRef(UUID.fromString(node.path("artifactId").asText()),node.path("artifactHash").asText(),node.path("language").asText(),node.path("runtime").asText());}catch(RuntimeException e){throw new IOException("Invalid artifact descriptor");}handles.add(acquire(execution,ref));count++;}}finally{handles.forEach(ArtifactCache.Handle::close);}
        }
        return count;
    }
}
