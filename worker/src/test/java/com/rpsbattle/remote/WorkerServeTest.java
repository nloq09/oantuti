package com.rpsbattle.remote;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class WorkerServeTest {
    @TempDir Path temp;
    @Test void continuousModePollsAnIdleBatchThenStopsOnRevokedIdentityWithoutReenrolling()throws Exception{
        var checks=new AtomicInteger();var claims=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);UUID id=UUID.randomUUID();String credential="wrc_"+"A".repeat(43);
        server.createContext("/internal/workers/"+id+"/",exchange->{String path=exchange.getRequestURI().getPath();assertEquals("Bearer "+credential,exchange.getRequestHeaders().getFirst("Authorization"));int code=200;String body="{}";
            if(path.endsWith("/identity")){if(checks.incrementAndGet()>1)code=401;else body="{\"workerId\":\""+id+"\",\"protocolVersion\":1}";}
            else if(path.endsWith("/assigned"))body="{\"jobs\":[]}";
            else if(path.endsWith("/capabilities"))body="{\"epoch\":1}";
            else if(path.endsWith("/heartbeat"))body="{\"status\":\"ONLINE\",\"approvalStatus\":\"APPROVED\"}";
            else if(path.endsWith("/claim")){claims.incrementAndGet();body="{\"jobs\":[]}";}
            else code=404;exchange.getRequestBody().readAllBytes();byte[] bytes=body.getBytes();exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            String url="http://127.0.0.1:"+server.getAddress().getPort();var config=WorkerConfig.parse(new String[]{"--central="+url,"--allow-insecure-http","--state-dir="+temp,"--serve"},Map.of());assertTrue(config.serve());
            assertThrows(IllegalArgumentException.class,()->WorkerConfig.parse(new String[]{"--central="+url,"--allow-insecure-http","--serve","--run-once"},Map.of()));
            var output=new ByteArrayOutputStream();try(var central=new CentralClient(URI.create(url));var cache=new ArtifactCache(temp.resolve("cache"),1048576)){
                assertEquals(1,new WorkerServe().run(central,new IdentityStore.Identity(url,id,credential),config,cache,new RuntimeProbe(config,args->true),new PrintStream(output)));
            }assertEquals(2,checks.get());assertEquals(1,claims.get());assertFalse(output.toString().contains(credential));
        }finally{server.stop(0);}
    }
}
