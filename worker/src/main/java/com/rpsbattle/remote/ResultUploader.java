package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Retries the same immutable result; never acknowledges a reflected/error response. */
public final class ResultUploader {
    private final CentralClient central;private final IdentityStore.Identity identity;private final ExecutionJournal journal;
    private final ObjectMapper json=new ObjectMapper();
    private static final List<String> FIELDS=List.of("executionId","matchId","winner","loser","reason","turnCount","durationMs","artifactHashA","artifactHashB","seed","replayChecksum");
    public ResultUploader(CentralClient central,IdentityStore.Identity identity,ExecutionJournal journal){this.central=central;this.identity=identity;this.journal=journal;}
    public int pending()throws IOException,InterruptedException {int count=0;IOException failure=null;for(var path:journal.entries())try{if(upload(path))count++;}catch(IOException e){failure=e;}if(failure!=null)throw new IOException("Pending result upload failed; state retained",failure);return count;}
    public boolean upload(Path dir)throws IOException,InterruptedException {
        var outcome=journal.outcome(dir);if(!"READY_FOR_UPLOAD".equals(outcome.path("status").asText()))return false;
        UUID execution=uuid(outcome.path("executionId").asText()),job=uuid(outcome.path("jobId").asText()),match=uuid(outcome.path("matchId").asText());
        var assignment=journal.assignment(dir);if(!identity.workerId().toString().equals(assignment.path("workerId").asText())||!execution.toString().equals(dir.getFileName().toString())||!job.toString().equals(assignment.path("jobId").asText())||!match.toString().equals(assignment.path("match").path("matchId").asText()))throw new IOException("Invalid outbox identity");
        byte[] replay=journal.replay(dir);if(!hash(replay).equals(outcome.path("replayChecksum").asText()))throw new IOException("Outbox replay checksum mismatch");
        var result=json.createObjectNode();for(String field:FIELDS){if(!outcome.has(field))throw new IOException("Incomplete outbox");result.set(field,outcome.get(field));}
        // Append after the legacy fields to retain the canonical receipt hash for old outboxes.
        if(outcome.hasNonNull("moveTiming"))result.set("moveTiming",outcome.get("moveTiming"));
        String expected=hash(json.writeValueAsBytes(result));
        var response=central.post(identity,"jobs/"+job+"/complete",Map.of("result",result,"replayBase64",Base64.getEncoder().encodeToString(replay)));
        if(!execution.toString().equals(response.path("executionId").asText())||!job.toString().equals(response.path("jobId").asText())||!match.toString().equals(response.path("matchId").asText())||!expected.equals(response.path("resultHash").asText())||!"ACCEPTED".equals(response.path("status").asText()))throw new IOException("Invalid result acknowledgement");
        journal.acknowledge(dir,outcome,expected);return true;
    }
    private static UUID uuid(String value)throws IOException {try{return UUID.fromString(value);}catch(Exception e){throw new IOException("Invalid outbox identity");}}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
