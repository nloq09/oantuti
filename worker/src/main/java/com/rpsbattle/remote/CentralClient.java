package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class CentralClient implements AutoCloseable {
    public static final String VERSION="0.7.0";
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
        if(!central.toString().equals(identity.central()) || !(Set.of("capabilities","heartbeat","jobs/claim","jobs/renew","jobs/recover").contains(route)||route.matches("jobs/[a-f0-9-]{36}/(decline|start|complete)")))throw new IOException("Invalid worker request");
        return send(HttpRequest.newBuilder(central.resolve("/internal/workers/"+identity.workerId()+"/"+route)).timeout(Duration.ofSeconds(route.endsWith("/complete")?60:15)).header("Authorization","Bearer "+identity.workerCredential()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),200);
    }
    public static class CentralFailure extends IOException {
        public final int status;
        CentralFailure(int status){super("Central request failed");this.status=status;}
    }
    public JsonNode assigned(IdentityStore.Identity identity)throws IOException,InterruptedException {
        checkOrigin(identity);return send(HttpRequest.newBuilder(central.resolve("/internal/workers/"+identity.workerId()+"/jobs/assigned")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+identity.workerCredential()).GET().build(),200);
    }
    private void checkOrigin(IdentityStore.Identity identity)throws IOException {if(!central.toString().equals(identity.central()))throw new IOException("Identity belongs to another Central");}
    public java.io.InputStream artifact(IdentityStore.Identity identity,UUID execution,ArtifactRef ref)throws IOException,InterruptedException {
        checkOrigin(identity);var request=HttpRequest.newBuilder(central.resolve("/internal/workers/"+identity.workerId()+"/artifacts/"+ref.artifactId()+"?executionId="+execution)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+identity.workerCredential()).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());var input=response.body();
        if(response.statusCode()!=200){input.close();throw new CentralFailure(response.statusCode());}
        if(!response.headers().firstValue("X-Artifact-SHA256").orElse("").equals(ref.artifactHash())||!response.headers().firstValue("X-Artifact-Language").orElse("").equals(ref.language())||!response.headers().firstValue("X-Artifact-Format").orElse("").equals(ref.format())||response.headers().firstValueAsLong("Content-Length").orElse(0)>ArtifactCache.ARTIFACT_LIMIT){input.close();throw new IOException("Invalid artifact response");}
        var timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"artifact-download-timeout");thread.setDaemon(true);return thread;});
        var deadline=timer.schedule(()->{try{input.close();}catch(IOException ignored){}},30,java.util.concurrent.TimeUnit.SECONDS);
        return new java.io.FilterInputStream(input){public void close()throws IOException{deadline.cancel(false);timer.shutdownNow();super.close();}};
    }
    private JsonNode send(HttpRequest request,int expected)throws IOException,InterruptedException {
        // Never echo response bodies or exception messages: an upstream error could reflect credentials.
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            if(response.statusCode()!=expected)throw new CentralFailure(response.statusCode());
            byte[] bytes=input.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Central response too large");
            return json.readTree(bytes);
        }
    }
    public void close(){http.close();}
}
