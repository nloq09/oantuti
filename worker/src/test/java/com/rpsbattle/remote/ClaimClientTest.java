package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClaimClientTest {
 @Test void retryUsesSameImmutableRequestAndAssignmentOwnershipIsChecked()throws Exception {
  var json=new ObjectMapper();UUID worker=UUID.randomUUID(),job=UUID.randomUUID(),execution=UUID.randomUUID();String credential="wrc_"+"Q".repeat(43);var requests=new ArrayList<String>();var wrong=new AtomicBoolean();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/workers/"+worker+"/jobs/claim",exchange->{assertEquals("Bearer "+credential,exchange.getRequestHeaders().getFirst("Authorization"));requests.add(new String(exchange.getRequestBody().readAllBytes()));byte[] bytes=json.writeValueAsBytes(Map.of("jobs",List.of(Map.of("jobId",job,"executionId",execution,"workerId",wrong.get()?UUID.randomUUID():worker,"epoch",2,"slot",1,"status","CLAIMED","leaseUntil","2030-01-01T00:00:00Z"))));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  try(var central=new CentralClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()))){var client=new ClaimClient(central,new IdentityStore.Identity("http://127.0.0.1:"+server.getAddress().getPort(),worker,credential));var hashes=new ArrayList<>(List.of("a".repeat(64)));var request=client.request(2,1,hashes,"1","classic-online-v1");hashes.clear();assertEquals(1,request.cachedArtifacts().size());assertEquals(execution.toString(),client.claim(request).path("jobs").get(0).path("executionId").asText());client.claim(request);assertEquals(requests.get(0),requests.get(1));wrong.set(true);assertThrows(java.io.IOException.class,()->client.claim(request));}
  finally{server.stop(0);}
 }
}
