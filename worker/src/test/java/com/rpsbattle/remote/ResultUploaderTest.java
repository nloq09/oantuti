package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ResultUploaderTest {
    @TempDir Path temp;final ObjectMapper json=new ObjectMapper();
    String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void lostAcknowledgementRetriesSameResultAndRestartSkipsAcknowledgedOutbox(boolean measured)throws Exception {
        UUID worker=UUID.randomUUID(),execution=UUID.randomUUID(),job=UUID.randomUUID();var spec=new HeadlessMatchTest().spec(8);var journal=new ExecutionJournal(temp.resolve("executions"));
        var node=json.valueToTree(Map.of("jobId",job,"executionId",execution,"workerId",worker,"epoch",1,"slot",1,"status","CLAIMED","leaseUntil",Instant.now().plusSeconds(120).toString(),"match",spec));var parsed=ExecutionJob.parse(node,worker,1);Path dir=journal.begin(parsed,node);journal.finish(dir,parsed,new HeadlessMatch().run(spec,new HeadlessMatchTest.Process(),new HeadlessMatchTest.Process(),System.nanoTime()+Duration.ofSeconds(10).toNanos()));
        if(!measured){var legacy=(com.fasterxml.jackson.databind.node.ObjectNode)journal.outcome(dir);legacy.remove("moveTiming");Files.writeString(dir.resolve("outcome.json"),json.writeValueAsString(legacy));}
        var requests=new AtomicInteger();var firstHash=new AtomicReference<String>();var badAck=new AtomicBoolean(false);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/workers/"+worker+"/jobs/"+job+"/complete",exchange->{try{
            assertEquals("Bearer wrc_"+"T".repeat(43),exchange.getRequestHeaders().getFirst("Authorization"));var body=json.readTree(exchange.getRequestBody());assertEquals(measured,body.path("result").has("moveTiming"));if(measured)assertTrue(body.path("result").path("moveTiming").path("countA").asInt()>0);String digest=hash(json.writeValueAsBytes(body.path("result")));if(firstHash.get()==null)firstHash.set(digest);else assertEquals(firstHash.get(),digest);
            assertEquals(body.path("result").path("replayChecksum").asText(),hash(Base64.getDecoder().decode(body.path("replayBase64").asText())));
            int count=requests.incrementAndGet();byte[] response=json.writeValueAsBytes(Map.of("executionId",execution,"jobId",job,"matchId",spec.matchId(),"resultHash",badAck.get()?"0".repeat(64):digest,"status","ACCEPTED"));exchange.sendResponseHeaders(count==1?503:200,response.length);exchange.getResponseBody().write(response);
        }catch(Exception e){throw new RuntimeException(e);}finally{exchange.close();}});server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        try(var central=new CentralClient(URI.create(base))){var uploader=new ResultUploader(central,new IdentityStore.Identity(base,worker,"wrc_"+"T".repeat(43)),journal);
            assertThrows(java.io.IOException.class,uploader::pending);assertEquals("READY_FOR_UPLOAD",journal.outcome(dir).path("status").asText());assertTrue(journal.pending());
            badAck.set(true);assertThrows(java.io.IOException.class,uploader::pending);assertEquals("READY_FOR_UPLOAD",journal.outcome(dir).path("status").asText());badAck.set(false);
            assertEquals(1,uploader.pending());assertEquals("ACKNOWLEDGED",journal.outcome(dir).path("status").asText());assertEquals(firstHash.get(),journal.outcome(dir).path("receiptHash").asText());assertFalse(new ExecutionJournal(temp.resolve("executions")).pending());assertEquals(0,uploader.pending());assertEquals(3,requests.get());
        }finally{server.stop(0);}
    }
    @Test void modifiedReplayNeverLeavesMachineAndUploadModeCannotBeCombinedWithRunMode()throws Exception {
        var journal=new ExecutionJournal(temp.resolve("executions"));UUID worker=UUID.randomUUID();var spec=new HeadlessMatchTest().spec(8);var node=json.valueToTree(Map.of("jobId",UUID.randomUUID(),"executionId",UUID.randomUUID(),"workerId",worker,"epoch",1,"slot",1,"status","CLAIMED","leaseUntil",Instant.now().plusSeconds(120).toString(),"match",spec));var job=ExecutionJob.parse(node,worker,1);Path dir=journal.begin(job,node);journal.finish(dir,job,new HeadlessMatch().run(spec,new HeadlessMatchTest.Process(),new HeadlessMatchTest.Process(),System.nanoTime()+Duration.ofSeconds(10).toNanos()));Files.writeString(dir.resolve("replay.json"),"corrupted");
        try(var central=new CentralClient(URI.create("http://127.0.0.1:1"))){assertThrows(java.io.IOException.class,()->new ResultUploader(central,new IdentityStore.Identity("http://127.0.0.1:1",worker,"wrc_"+"T".repeat(43)),journal).pending());}assertEquals("READY_FOR_UPLOAD",journal.outcome(dir).path("status").asText());assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=https://central.example","--run-once","--upload-pending"},Map.of()));
    }
}
