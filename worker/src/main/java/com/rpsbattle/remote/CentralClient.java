package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class CentralClient implements AutoCloseable {
    public static final String VERSION="0.2.0";
    private final URI central;
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public CentralClient(URI central){this.central=central;}
    public IdentityStore.Identity register(String token,String name)throws IOException,InterruptedException {
        if(token==null || !token.matches("wrt_[A-Za-z0-9_-]{43}"))throw new IOException("Registration token required");
        var request=HttpRequest.newBuilder(central.resolve("/internal/workers/register")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("registrationToken",token,"name",name,"workerVersion",VERSION,"protocolVersion",1)))).build();
        var result=send(request,201); var worker=result.path("worker");
        var credential=result.path("workerCredential").asText();
        if(worker.path("protocolVersion").asInt()!=1 || !credential.matches("wrc_[A-Za-z0-9_-]{43}"))throw new IOException("Invalid Central registration response");
        UUID id;try{id=UUID.fromString(worker.path("workerId").asText());}catch(IllegalArgumentException e){throw new IOException("Invalid Central identity");}
        return new IdentityStore.Identity(central.toString(),id,credential);
    }
    public JsonNode identity(IdentityStore.Identity identity)throws IOException,InterruptedException {
        if(!central.toString().equals(identity.central()))throw new IOException("Identity belongs to another Central");
        var request=HttpRequest.newBuilder(central.resolve("/internal/workers/"+identity.workerId()+"/identity")).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+identity.workerCredential()).GET().build();
        var result=send(request,200);
        if(!result.path("workerId").asText().equals(identity.workerId().toString()) || result.path("protocolVersion").asInt()!=1)throw new IOException("Central identity mismatch");
        return result;
    }
    public JsonNode post(IdentityStore.Identity identity,String route,Object payload)throws IOException,InterruptedException {
        if(!central.toString().equals(identity.central()) || !Set.of("capabilities","heartbeat").contains(route))throw new IOException("Invalid worker request");
        return send(HttpRequest.newBuilder(central.resolve("/internal/workers/"+identity.workerId()+"/"+route)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+identity.workerCredential()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),200);
    }
    public static class CentralFailure extends IOException {
        public final int status;
        CentralFailure(int status){super("Central request failed");this.status=status;}
    }
    private JsonNode send(HttpRequest request,int expected)throws IOException,InterruptedException {
        // Never echo response bodies or exception messages: an upstream error could reflect credentials.
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            if(response.statusCode()!=expected)throw new CentralFailure(response.statusCode());
            byte[] bytes=input.readNBytes(16385);if(bytes.length>16384)throw new IOException("Central response too large");
            return json.readTree(bytes);
        }
    }
    public void close(){http.close();}
}
