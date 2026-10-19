package com.rpsbattle.remote;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorkerStartupTest {
    @TempDir Path temp;
    static final String TOKEN="wrt_"+"A".repeat(43),CREDENTIAL="wrc_"+"B".repeat(43);
    @Test void httpsIsDefaultAndPlainHttpRequiresExplicitLocalOptIn() {
        assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=http://localhost:8080"},Map.of()));
        for(String host:List.of("example.com","8.8.8.8","169.254.169.254","10.1.2.999"))assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central=http://"+host,"--allow-insecure-http"},Map.of()));
        for(String host:List.of("localhost","127.0.0.1","192.168.0.105","10.0.0.1","172.16.0.1","[::1]"))assertNotNull(WorkerConfig.parse(new String[]{"--central=http://"+host,"--allow-insecure-http"},Map.of()));
        assertNotNull(WorkerConfig.parse(new String[]{"--central=https://example.com"},Map.of()));
        for(String url:List.of("https://user:secret@example.com","https://example.com?q=secret","https://example.com/secret","https://example.com#secret"))assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central="+url},Map.of()));
    }
    @Test void identityPersistsPrivatelyAndCannotBeOverwrittenOrUsedConcurrently()throws Exception {
        var identity=new IdentityStore.Identity("https://central.example",UUID.randomUUID(),CREDENTIAL);
        try(var store=new IdentityStore(temp.resolve("state"))) {
            assertNull(store.load());store.save(identity);assertEquals(identity,store.load());
            assertThrows(IOException.class,()->store.save(identity));
            assertThrows(Exception.class,()->new IdentityStore(temp.resolve("state")));
        }
        try(var restarted=new IdentityStore(temp.resolve("state"))){assertEquals(identity,restarted.load());}
        var file=temp.resolve("state/identity.json");
        if(Files.getFileAttributeView(file,PosixFileAttributeView.class)!=null)assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(file));
        else {var acl=Files.getFileAttributeView(file,AclFileAttributeView.class);assertNotNull(acl);var owner=acl.getOwner();assertTrue(acl.getAcl().stream().filter(a->a.type()==AclEntryType.ALLOW).allMatch(a->a.principal().equals(owner)));}
        assertFalse(identity.toString().contains(CREDENTIAL));
    }
    @Test void startupRegistersOnceThenReusesIdentityWithoutTokenAndRejectsDifferentCentral()throws Exception {
        var json=new ObjectMapper();UUID id=UUID.randomUUID();var registrations=new AtomicInteger();var checks=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/workers/register",exchange->{
            registrations.incrementAndGet();var request=json.readTree(exchange.getRequestBody());assertEquals(TOKEN,request.path("registrationToken").asText());
            byte[] bytes=json.writeValueAsBytes(Map.of("worker",Map.of("workerId",id.toString(),"protocolVersion",1),"workerCredential",CREDENTIAL));exchange.sendResponseHeaders(201,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/internal/workers/"+id+"/identity",exchange->{checks.incrementAndGet();assertEquals("Bearer "+CREDENTIAL,exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes=json.writeValueAsBytes(Map.of("workerId",id.toString(),"protocolVersion",1,"status","STARTING","approvalStatus","PENDING_APPROVAL"));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var args=new String[]{"--central="+url,"--state-dir="+temp.resolve("state"),"--allow-insecure-http"};
            var output=new ByteArrayOutputStream();var log=new PrintStream(output);
            assertEquals(0,WorkerApplication.run(args,Map.of("WORKER_REGISTRATION_TOKEN",TOKEN),log));
            assertEquals(0,WorkerApplication.run(args,Map.of(),log));assertEquals(1,registrations.get());assertEquals(2,checks.get());
            assertTrue(output.toString().contains("PENDING_APPROVAL"));assertFalse(output.toString().contains(TOKEN));assertFalse(output.toString().contains(CREDENTIAL));
            assertFalse(Files.readString(temp.resolve("state/identity.json")).contains(TOKEN));
            try(var central=new CentralClient(URI.create("https://other.example"));var store=new IdentityStore(temp.resolve("state"))){assertThrows(IOException.class,()->central.identity(store.load()));}
        } finally {server.stop(0);}
    }
    @Test void redirectsAndRejectedEnrollmentNeverPersistIdentityOrPrintReflectedSecrets()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/workers/register",exchange->{exchange.getResponseHeaders().add("Location","https://example.com");byte[] bytes=(TOKEN+CREDENTIAL).getBytes();exchange.sendResponseHeaders(302,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {var output=new ByteArrayOutputStream();assertEquals(1,WorkerApplication.run(new String[]{"--central=http://127.0.0.1:"+server.getAddress().getPort(),"--state-dir="+temp.resolve("state"),"--allow-insecure-http"},Map.of("WORKER_REGISTRATION_TOKEN",TOKEN),new PrintStream(output)));assertFalse(Files.exists(temp.resolve("state/identity.json")));assertFalse(output.toString().contains(TOKEN));assertFalse(output.toString().contains(CREDENTIAL));}
        finally{server.stop(0);}
    }
}
