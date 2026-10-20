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
class RecoveryAgentTest {
    @TempDir Path temp;final ObjectMapper json=new ObjectMapper();
    JsonNode assignment(UUID worker,UUID execution){return json.valueToTree(Map.of("jobId",UUID.randomUUID(),"executionId",execution,"workerId",worker,"epoch",1,"slot",1,"status","CLAIMED","leaseUntil",Instant.now().plusSeconds(120).toString(),"match",new HeadlessMatchTest().spec(8)));}
    @Test void interruptedPartialAndUntrackedClaimsAreAbandonedOnceWithoutRunningBots()throws Exception {
        UUID worker=UUID.randomUUID(),execution=UUID.randomUUID(),orphan=UUID.randomUUID();var journal=new ExecutionJournal(temp.resolve("executions"));var node=assignment(worker,execution);Path dir=journal.orphan(execution,node);Files.writeString(dir.resolve("outcome.json.tmp"),"partial");Files.writeString(dir.resolve("outcome.json"),"{");
        var pending=new ArrayList<JsonNode>(List.of(assignment(worker,orphan)));var calls=new AtomicInteger();var cleaned=new ArrayList<UUID>();var badAck=new AtomicBoolean(true);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/workers/"+worker,exchange->{try{Object response;
            if(exchange.getRequestURI().getPath().endsWith("/jobs/assigned"))response=Map.of("jobs",List.copyOf(pending));
            else{var body=json.readTree(exchange.getRequestBody());assertEquals("ABANDON",body.path("operation").asText());UUID id=UUID.fromString(body.path("executionId").asText());calls.incrementAndGet();pending.removeIf(v->v.path("executionId").asText().equals(id.toString()));response=Map.of("executionId",badAck.get()?UUID.randomUUID():id,"jobId",UUID.randomUUID(),"status","ABANDONED");}
            byte[] bytes=json.writeValueAsBytes(response);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        }finally{exchange.close();}});server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        try(var central=new CentralClient(URI.create(base))){var recovery=new RecoveryAgent(central,new IdentityStore.Identity(base,worker,"wrc_"+"A".repeat(43)),journal,cleaned::add);
            assertThrows(java.io.IOException.class,()->recovery.reconcile(true));assertTrue(cleaned.isEmpty());assertTrue(Files.exists(dir.resolve("outcome.json.tmp")));badAck.set(false);
            assertEquals(2,recovery.reconcile(true));assertEquals(Set.of(execution,orphan),new HashSet<>(cleaned));assertEquals("RECOVERED",journal.outcome(dir).path("status").asText());assertFalse(Files.exists(dir.resolve("outcome.json.tmp")));assertFalse(journal.pending());assertEquals(0,recovery.reconcile(true));assertEquals(4,calls.get());
        }finally{server.stop(0);}
    }
    @Test void finishedResultRecoversWithinGraceAndSupersededReplayIsKeptWithoutReplayingMatch()throws Exception {
        for(boolean superseded:List.of(false,true)){
            Path root=temp.resolve("case-"+superseded);var journal=new ExecutionJournal(root);UUID worker=UUID.randomUUID(),execution=UUID.randomUUID();var node=assignment(worker,execution);var job=ExecutionJob.parse(node,worker,1);Path dir=journal.begin(job,node);journal.finish(dir,job,new HeadlessMatch().run(job.match(),new HeadlessMatchTest.Process(),new HeadlessMatchTest.Process(),System.nanoTime()+Duration.ofSeconds(10).toNanos()));
            var posts=new AtomicInteger();var cleaned=new ArrayList<UUID>();
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/workers/"+worker,exchange->{try{
                var body=json.readTree(exchange.getRequestBody());Object response;int status=200;
                if(exchange.getRequestURI().getPath().endsWith("/recover")){assertEquals("RESULT_READY",body.path("operation").asText());response=Map.of("executionId",execution,"jobId",job.jobId(),"status",superseded?"SUPERSEDED":"UPLOAD_ALLOWED");}
                else if(posts.incrementAndGet()==1){status=409;response=Map.of("error","EXECUTION_NOT_COMPLETABLE");}
                else{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(body.path("result"))));response=Map.of("executionId",execution,"jobId",job.jobId(),"matchId",job.match().matchId(),"status","ACCEPTED","resultHash",hash);}
                byte[] bytes=json.writeValueAsBytes(response);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Exception e){throw new RuntimeException(e);}finally{exchange.close();}});server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
            try(var central=new CentralClient(URI.create(base))){assertEquals(1,new RecoveryAgent(central,new IdentityStore.Identity(base,worker,"wrc_"+"B".repeat(43)),journal,cleaned::add).reconcile(false));assertEquals(superseded?"SUPERSEDED":"ACKNOWLEDGED",journal.outcome(dir).path("status").asText());assertTrue(Files.exists(dir.resolve("replay.json")));assertTrue(journal.outcome(dir).has("replayChecksum"));assertEquals(superseded?List.of(execution):List.of(),cleaned);assertFalse(journal.pending());}
            finally{server.stop(0);}
        }
        assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=https://central.example","--recover","--run-once"},Map.of()));
    }
}
