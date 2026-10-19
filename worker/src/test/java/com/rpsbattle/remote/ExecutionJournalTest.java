package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.replay.ReplayService;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionJournalTest {
    @TempDir Path temp;
    final ObjectMapper json=new ObjectMapper();
    @Test void intentAndFinishedReplaySurviveRestartAndCannotBeExecutedTwice()throws Exception {
        var spec=new HeadlessMatchTest().spec(8);UUID worker=UUID.randomUUID(),execution=UUID.randomUUID();
        var node=json.valueToTree(Map.of("jobId",UUID.randomUUID(),"executionId",execution,"workerId",worker,"epoch",1,"slot",1,"status","CLAIMED","leaseUntil",Instant.now().plusSeconds(120).toString(),"match",spec));
        var job=ExecutionJob.parse(node,worker,1);var journal=new ExecutionJournal(temp.resolve("executions"));assertFalse(journal.pending());var dir=journal.begin(job,node);
        assertTrue(new ExecutionJournal(temp.resolve("executions")).pending());assertEquals("STARTING",json.readTree(dir.resolve("outcome.json").toFile()).path("status").asText());assertThrows(FileAlreadyExistsException.class,()->journal.begin(job,node));
        Path cached=temp.resolve("cached");Files.writeString(cached,"tampered");assertThrows(java.io.IOException.class,()->journal.stage(dir,"A",cached,spec.botA()));journal.clearStaged(dir);assertFalse(Files.exists(dir.resolve("bot-A.bin")));
        var result=new HeadlessMatch().run(spec,new HeadlessMatchTest.Process(),new HeadlessMatchTest.Process(),System.nanoTime()+Duration.ofSeconds(10).toNanos());journal.finish(dir,job,result);
        var outcome=json.readTree(dir.resolve("outcome.json").toFile());assertEquals("READY_FOR_UPLOAD",outcome.path("status").asText());assertEquals(spec.botA().artifactHash(),outcome.path("artifactHashA").asText());assertTrue(outcome.path("replayChecksum").asText().matches("[a-f0-9]{64}"));assertTrue(new ReplayService().replay(dir.resolve("replay.json")).isFinished());assertTrue(new ExecutionJournal(temp.resolve("executions")).pending());
        journal.failure(dir,job,"EXECUTION_FAILED");outcome=json.readTree(dir.resolve("outcome.json").toFile());assertEquals("FAILED",outcome.path("status").asText());assertFalse(outcome.has("winner"));
    }
    @Test void incompatibleJobsExpiredLeasesAndConflictingCliModesAreRejectedBeforeExecution()throws Exception {
        var spec=new HeadlessMatchTest().spec(8);UUID worker=UUID.randomUUID();var node=json.valueToTree(Map.of("jobId",UUID.randomUUID(),"executionId",UUID.randomUUID(),"workerId",worker,"epoch",1,"slot",1,"status","CLAIMED","leaseUntil",Instant.now().minusSeconds(1).toString(),"match",spec));assertThrows(java.io.IOException.class,()->ExecutionJob.parse(node,worker,1));
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("leaseUntil",Instant.now().plusSeconds(120).toString());((com.fasterxml.jackson.databind.node.ObjectNode)node.path("match")).put("gameCoreVersion","unsupported");assertThrows(java.io.IOException.class,()->ExecutionJob.parse(node,worker,1));
        assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=https://central.example","--run-once","--prefetch"},Map.of()));assertTrue(WorkerConfig.parse(new String[]{"--central=https://central.example","--run-once"},Map.of()).runOnce());
    }
}
