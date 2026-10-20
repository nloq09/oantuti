package com.rpsbattle.remote;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.bot.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SandboxBotDockerTest {
    @TempDir Path temp;
    @BeforeEach void available()throws Exception {
        Process process=null;boolean ready=false;
        try{process=new ProcessBuilder("docker","info","--format","{{.OSType}}") .redirectError(ProcessBuilder.Redirect.DISCARD).start();ready=process.waitFor(5,TimeUnit.SECONDS)&&process.exitValue()==0&&new String(process.getInputStream().readAllBytes()).strip().equals("linux");}
        catch(java.io.IOException ignored){}finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
        Assumptions.assumeTrue(ready,"Docker Linux required for sandbox integration");
    }
    ArtifactRef ref(String lang){return new ArtifactRef(UUID.randomUUID(),"a".repeat(64),lang,Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").get(lang));}
    void noContainers(UUID execution)throws Exception {
        var process=new ProcessBuilder("docker","ps","-aq","--filter","label=rps.execution="+execution).start();assertTrue(process.waitFor(5,TimeUnit.SECONDS));assertEquals(0,process.exitValue());assertEquals("",new String(process.getInputStream().readAllBytes()).strip());
    }
    @Test void pythonIsPersistentNonRootReadOnlyAndRemovedAtMatchEnd()throws Exception {
        UUID execution=UUID.randomUUID();Path file=temp.resolve("artifact");Files.writeString(file,"import sys,json,os\nn=0\nfor line in sys.stdin:\n n+=1\n try:\n  open('/etc/remote-write','w').write('x')\n  writable=True\n except OSError: writable=False\n print(json.dumps({'n':n,'pid':os.getpid(),'uid':os.getuid(),'writable':writable,'socket':os.path.exists('/var/run/docker.sock')}),flush=True)\n");
        try(var bot=new SandboxBot(file,ref("PYTHON"),execution,System.nanoTime()+Duration.ofSeconds(20).toNanos())){
            bot.start();var json=new ObjectMapper();var first=json.readTree(bot.exchange("{}",Duration.ofSeconds(2)));var second=json.readTree(bot.exchange("{}",Duration.ofSeconds(2)));assertEquals(1,first.path("n").asInt());assertEquals(2,second.path("n").asInt());assertEquals(first.path("pid"),second.path("pid"));assertEquals(65534,first.path("uid").asInt());assertFalse(first.path("writable").asBoolean());assertFalse(first.path("socket").asBoolean());assertTrue(bot.isAlive());
        }noContainers(execution);
    }
    @Test void recoveryOnlyRemovesContainersWithTheExactExecutionLabel()throws Exception {
        UUID stale=UUID.randomUUID(),other=UUID.randomUUID();var containers=new ArrayList<String>();
        try{
            for(var execution:List.of(stale,other)){
                var process=new ProcessBuilder("docker","run","-d","--rm","--network=none","--read-only","--user=65534:65534","--memory=64m","--cpus=0.25","--label","rps.scope=remote-worker","--label","rps.execution="+execution,"python:3.12-slim","python","-c","import time;time.sleep(60)").redirectError(ProcessBuilder.Redirect.DISCARD).start();assertTrue(process.waitFor(10,TimeUnit.SECONDS));assertEquals(0,process.exitValue());containers.add(new String(process.getInputStream().readAllBytes()).strip());
            }
            RecoveryAgent.removeContainers(stale);noContainers(stale);
            var inspect=new ProcessBuilder("docker","inspect","--format","{{.State.Running}}",containers.get(1)).start();assertTrue(inspect.waitFor(5,TimeUnit.SECONDS));assertEquals("true",new String(inspect.getInputStream().readAllBytes()).strip());
        }finally{for(String id:containers){var cleanup=new ProcessBuilder("docker","rm","-f",id).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!cleanup.waitFor(5,TimeUnit.SECONDS))cleanup.destroyForcibly();}}
    }
    @Test void timeoutAndOutputFloodAreBoundedAndCleanedUp()throws Exception {
        for(String source:List.of("import sys,time\nfor line in sys.stdin: time.sleep(10)\n","import sys\nfor line in sys.stdin: print('x'*70000,flush=True)\n","import sys\nfor line in sys.stdin: sys.stderr.write('x'*70000);sys.stderr.flush()\n")){
            UUID execution=UUID.randomUUID();Path file=temp.resolve(execution+".py");Files.writeString(file,source);
            try(var bot=new SandboxBot(file,ref("PYTHON"),execution,System.nanoTime()+Duration.ofSeconds(20).toNanos())){bot.start();assertThrows(BotProtocolException.class,()->bot.exchange("{}",Duration.ofMillis(500)));}noContainers(execution);
        }
    }
    @Test void precompiledCppAndJavaRunWithoutRebuildingInsideMatch()throws Exception {
        for(String lang:List.of("CPP","JAVA")){
            String name=lang.equals("CPP")?"main.cpp":"Main.java",source=lang.equals("CPP")?"#include <iostream>\n#include <string>\nint main(){std::string s;int n=0;while(std::getline(std::cin,s))std::cout<<++n<<std::endl;}\n":"import java.io.*; public class Main { public static void main(String[] x)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));int n=0;while(r.readLine()!=null)System.out.println(++n);} }";
            Path input=temp.resolve(name);Files.writeString(input,source);String script=lang.equals("CPP")?"g++ /input -x c++ -o /tmp/bot >&2 && base64 /tmp/bot":"cp /input /tmp/Main.java && javac -J-Xmx128m -d /tmp /tmp/Main.java >&2 && jar --create --file /tmp/bot.jar --main-class Main -C /tmp Main.class >&2 && base64 /tmp/bot.jar";
            if(lang.equals("CPP"))script="g++ -x c++ /input -o /tmp/bot >&2 && base64 /tmp/bot";
            var compile=new ProcessBuilder("docker","run","--rm","--network=none","--memory=384m","--cpus=1","--mount","type=bind,source="+input.toAbsolutePath()+",target=/input,readonly",ref(lang).runtime(),"sh","-c",script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var future=CompletableFuture.supplyAsync(()->{try{return compile.getInputStream().readNBytes(8*1024*1024);}catch(java.io.IOException e){throw new RuntimeException(e);}});assertTrue(compile.waitFor(30,TimeUnit.SECONDS));assertEquals(0,compile.exitValue());Path binary=temp.resolve(lang+".bin");Files.write(binary,Base64.getMimeDecoder().decode(future.get(5,TimeUnit.SECONDS)));
            UUID execution=UUID.randomUUID();try(var bot=new SandboxBot(binary,ref(lang),execution,System.nanoTime()+Duration.ofSeconds(20).toNanos())){bot.start();assertEquals("1",bot.exchange("{}",Duration.ofSeconds(3)));assertEquals("2",bot.exchange("{}",Duration.ofSeconds(3)));}noContainers(execution);
        }
    }
}
