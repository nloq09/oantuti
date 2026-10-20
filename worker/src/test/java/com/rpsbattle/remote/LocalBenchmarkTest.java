package com.rpsbattle.remote;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class LocalBenchmarkTest {
    @TempDir Path temp;
    @Test void baselineUsesRealSandboxWithoutCentralOrEnrollmentAndDoesNotClaimParallelCapacity()throws Exception{
        var config=WorkerConfig.parse(new String[]{"--central=https://benchmark.invalid","--state-dir="+temp},Map.of());var probe=new RuntimeProbe(config);Assumptions.assumeTrue(Boolean.TRUE.equals(probe.capabilities().get("docker"))&&((Set<?>)probe.capabilities().get("supportedLanguages")).contains("PYTHON"));
        var output=new ByteArrayOutputStream();new LocalBenchmark().run(config,new PrintStream(output));var result=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.toString());assertEquals(1,result.path("measuredConcurrentMatches").asInt());assertEquals(1,result.path("recommendedMatchSlots").asInt());assertEquals(32,result.path("turnCount").asInt());assertFalse(Files.exists(temp.resolve("identity.json")));try(var entries=Files.list(temp)){assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith("benchmark-")));}
        var check=new ProcessBuilder("docker","ps","-aq","--filter","label=rps.execution="+result.path("executionId").asText()).redirectError(ProcessBuilder.Redirect.DISCARD).start();assertTrue(check.waitFor(5,TimeUnit.SECONDS));assertTrue(new String(check.getInputStream().readAllBytes()).isBlank());
    }
}
