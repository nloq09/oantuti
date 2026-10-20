package com.rpsbattle.remote;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** A measured one-match baseline. It cannot claim that eight parallel matches were tested. */
public final class LocalBenchmark {
    public void run(WorkerConfig config,PrintStream out)throws Exception{
        var probe=new RuntimeProbe(config);var caps=probe.capabilities();
        if(!Boolean.TRUE.equals(caps.get("docker"))||!((Set<?>)caps.get("supportedLanguages")).contains("PYTHON"))throw new IOException("Docker Linux and Python runtime required");
        try(var state=new IdentityStore(config.stateDirectory())){
            UUID execution=UUID.randomUUID();Path directory=Files.createTempDirectory(config.stateDirectory(),"benchmark-"),file=directory.resolve("main.py");IdentityStore.secure(directory);
            String source="import sys,json\nfor line in sys.stdin:\n r=json.loads(line)\n print(json.dumps({'type':'ACTION','sequence':r['sequence'],'action':r['legalActions'][0]}),flush=True)\n";
            Files.writeString(file,source);if(Files.getFileAttributeView(file,java.nio.file.attribute.PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            var ref=new ArtifactRef(UUID.randomUUID(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))),"PYTHON","python:3.12-slim");
            var spec=new ExecutionJob.Spec(UUID.randomUUID(),UUID.randomUUID(),ref,ref,"1","classic-online-v1",1L,2000,45000,32,"TRUSTED");long start=System.nanoTime(),deadline=start+Duration.ofSeconds(45).toNanos();
            var a=new SandboxBot(file,ref,execution,deadline);var b=new SandboxBot(file,ref,execution,deadline);Thread shutdown=new Thread(()->{a.close();b.close();},"benchmark-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);var timer=Executors.newSingleThreadScheduledExecutor();var expiry=timer.schedule(()->{a.close();b.close();},45,TimeUnit.SECONDS);
            try(a;b){var result=new HeadlessMatch().run(spec,a,b,deadline);if(!result.reason().equals("ENGINE_RESULT"))throw new IOException("Baseline bot failed");var report=new LinkedHashMap<String,Object>();report.put("mode","LOCAL_DOCKER_SINGLE_MATCH_BASELINE");report.put("executionId",execution);report.put("measuredConcurrentMatches",1);report.put("recommendedMatchSlots",1);report.put("turnCount",result.turnCount());report.put("elapsedMs",(System.nanoTime()-start)/1000000);report.put("capabilities",caps);report.put("hostSampleAfter",probe.heartbeat());report.put("note","One match with two persistent sandboxed Python bots; parallel capacity was not measured. No enrollment, claim or result upload.");out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report));}
            finally{expiry.cancel(false);timer.shutdownNow();try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){}Files.deleteIfExists(file);Files.deleteIfExists(directory);}
        }
    }
}
