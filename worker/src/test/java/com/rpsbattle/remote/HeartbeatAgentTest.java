package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HeartbeatAgentTest {
    @TempDir Path temp;
    final ObjectMapper json=new ObjectMapper();
    @Test void networkRetryKeepsCredentialAndSequenceAndStaleSessionRenegotiatesCapabilities()throws Exception {
        UUID id=UUID.randomUUID();String secret="wrc_"+"X".repeat(43);var epochs=new AtomicInteger();var code=new AtomicInteger(503);var sequences=new ArrayList<Long>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/workers/"+id,exchange->{
            assertEquals("Bearer "+secret,exchange.getRequestHeaders().getFirst("Authorization"));
            Object result;int status=200;
            if(exchange.getRequestURI().getPath().endsWith("/capabilities")){json.readTree(exchange.getRequestBody());result=Map.of("epoch",epochs.incrementAndGet());}
            else {var request=json.readTree(exchange.getRequestBody());sequences.add(request.path("sequence").asLong());status=code.getAndSet(200);result=status==200?Map.of("status","ONLINE","approvalStatus","APPROVED"):Map.of("error",secret);}
            byte[] bytes=json.writeValueAsBytes(result);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        var output=new ByteArrayOutputStream();
        try(var client=new CentralClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()))) {
            var identity=new IdentityStore.Identity("http://127.0.0.1:"+server.getAddress().getPort(),id,secret);
            var agent=new HeartbeatAgent(client,identity,Map.of("docker",true),()->Map.of("freeSlots",1),new PrintStream(output));
            assertThrows(CentralClient.CentralFailure.class,agent::tick);agent.tick();assertEquals(1,epochs.get());assertEquals(List.of(1L,2L),sequences);
            code.set(409);assertThrows(CentralClient.CentralFailure.class,agent::tick);agent.tick();assertEquals(2,epochs.get());assertEquals(1L,sequences.getLast());
            code.set(401);assertEquals(1,agent.run(true));assertFalse(output.toString().contains(secret));
        }finally{server.stop(0);}
    }
    @Test void probeAdvertisesOnlyAvailableImagesAndUnknownCpuIsNotInvented()throws Exception {
        var config=WorkerConfig.parse(new String[]{"--central=https://central.example","--state-dir="+temp,"--max-matches=3","--max-bots=6"},Map.of());
        var commands=new ArrayList<List<String>>();
        var available=new RuntimeProbe(config,args->{commands.add(args);return args.getFirst().equals("info")||args.getLast().equals("python:3.12-slim");});
        assertEquals(Set.of("PYTHON"),available.capabilities().get("supportedLanguages"));assertEquals(3,available.heartbeat().get("freeSlots"));assertEquals(4,commands.size());assertTrue(commands.stream().noneMatch(c->c.contains("pull")));
        var absent=new RuntimeProbe(config,args->false);assertEquals(Set.of(),absent.capabilities().get("supportedLanguages"));assertEquals(false,absent.capabilities().get("docker"));assertEquals(0,absent.heartbeat().get("freeSlots"));
        assertEquals(0,absent.heartbeat().get("activeMatches"));assertEquals(0L,absent.heartbeat().get("cacheSizeMb"));
        for(var key:List.of("cpuUsage","memoryUsage")){var value=(Double)absent.heartbeat().get(key);assertTrue(value==null||value>=0&&value<=100);}
    }
    @Test void resourceLimitsRequireTwoBotProcessesPerConfiguredMatch() {
        for(var args:List.of(new String[]{"--central=https://central.example","--max-matches=3","--max-bots=5"},new String[]{"--central=https://central.example","--max-matches=0"},new String[]{"--central=https://central.example","--max-matches=257"}))assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(args,Map.of()));
        var config=WorkerConfig.parse(new String[]{"--central=https://central.example","--once"},Map.of("WORKER_MAX_MATCHES","4"));assertEquals(8,config.maxBots());assertTrue(config.once());
    }
}
