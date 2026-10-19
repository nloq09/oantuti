package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ArtifactClientTest {
 @TempDir Path temp;
 String hash(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes()));}
 @Test void assignedPrewarmDownloadsOnceThenUsesHashCacheAcrossRestart()throws Exception {
  UUID worker=UUID.randomUUID(),execution=UUID.randomUUID(),artifact=UUID.randomUUID();String hash=hash("hello"),credential="wrc_"+"K".repeat(43);var downloads=new AtomicInteger();var json=new ObjectMapper();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/workers/"+worker+"/jobs/assigned",exchange->{var ref=Map.of("artifactId",artifact,"artifactHash",hash,"language","PYTHON","runtime","python:3.12-slim");byte[] bytes=json.writeValueAsBytes(Map.of("jobs",List.of(Map.of("executionId",execution,"workerId",worker,"leaseUntil","2030-01-01T00:00:00Z","status","CLAIMED","match",Map.of("botA",ref,"botB",ref)))));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
  server.createContext("/internal/workers/"+worker+"/artifacts/"+artifact,exchange->{downloads.incrementAndGet();assertEquals("Bearer "+credential,exchange.getRequestHeaders().getFirst("Authorization"));assertEquals("executionId="+execution,exchange.getRequestURI().getQuery());exchange.getResponseHeaders().add("X-Artifact-SHA256",hash);exchange.getResponseHeaders().add("X-Artifact-Language","PYTHON");exchange.getResponseHeaders().add("X-Artifact-Format","PYTHON_SOURCE");byte[] bytes="hello".getBytes();exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  try(var central=new CentralClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()))){var identity=new IdentityStore.Identity("http://127.0.0.1:"+server.getAddress().getPort(),worker,credential);try(var cache=new ArtifactCache(temp.resolve("cache"),100)){var client=new ArtifactClient(central,identity,cache);assertEquals(2,client.prefetchAssigned());assertEquals(2,client.prefetchAssigned());assertEquals(1,downloads.get());assertEquals(3,cache.stats().hits());}try(var restarted=new ArtifactCache(temp.resolve("cache"),100)){assertEquals(2,new ArtifactClient(central,identity,restarted).prefetchAssigned());assertEquals(1,downloads.get());}}
  finally{server.stop(0);}
 }
 @Test void corruptPayloadAndRedirectCannotPopulateCacheOrFollowCredentialToOtherHost()throws Exception {
  UUID worker=UUID.randomUUID(),execution=UUID.randomUUID(),artifact=UUID.randomUUID();String hash=hash("hello"),credential="wrc_"+"M".repeat(43);var status=new AtomicInteger(200);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/workers/"+worker+"/artifacts/"+artifact,exchange->{exchange.getResponseHeaders().add("X-Artifact-SHA256",hash);exchange.getResponseHeaders().add("X-Artifact-Language","PYTHON");exchange.getResponseHeaders().add("X-Artifact-Format","PYTHON_SOURCE");exchange.getResponseHeaders().add("Location","https://example.invalid");byte[] bytes="wrong".getBytes();exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  try(var central=new CentralClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));var cache=new ArtifactCache(temp.resolve("cache"),100)) {var client=new ArtifactClient(central,new IdentityStore.Identity("http://127.0.0.1:"+server.getAddress().getPort(),worker,credential),cache);var ref=new ArtifactRef(artifact,hash,"PYTHON","python:3.12-slim");assertThrows(IOException.class,()->client.acquire(execution,ref));assertEquals(List.of(),cache.hashes());status.set(302);assertThrows(CentralClient.CentralFailure.class,()->client.acquire(execution,ref));assertEquals(0,cache.stats().bytes());}
  finally{server.stop(0);}
 }
}
