package com.rpsbattle.remote;

import com.fasterxml.jackson.databind.*;
import com.rpsbattle.bot.*;
import com.rpsbattle.replay.*;
import com.rpsbattle.online.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HeadlessMatchTest {
    @Test void gameplaySignalOccursAfterBothBotsStartAndPreparationAndFailureDoesNotInventResult(){
        var a=new Process();var b=new Process();var calls=new java.util.concurrent.atomic.AtomicInteger();new HeadlessMatch().run(spec(8),a,b,System.nanoTime()+Duration.ofSeconds(10).toNanos(),()->{assertEquals(1,a.starts);assertEquals(1,b.starts);calls.incrementAndGet();});assertEquals(1,calls.get());
        var broken=new Process();var other=new Process();assertThrows(IllegalStateException.class,()->new HeadlessMatch().run(spec(8),broken,other,System.nanoTime()+Duration.ofSeconds(10).toNanos(),()->{throw new IllegalStateException("No ACK");}));assertEquals(1,broken.closes);assertEquals(1,other.closes);
    }
    final ObjectMapper json=new ObjectMapper();
    ExecutionJob.Spec spec(int turns) {var ref=new ArtifactRef(UUID.randomUUID(),"a".repeat(64),"PYTHON","python:3.12-slim");return new ExecutionJob.Spec(UUID.randomUUID(),UUID.randomUUID(),ref,ref,"1","classic-online-v1",Long.MAX_VALUE,1000,60000,turns,"TRUSTED");}
    static class Process implements BotProcess {
        int starts,requests,closes;String mode="PASS";final ObjectMapper json=new ObjectMapper();
        public void start(){starts++;}
        public String exchange(String request,Duration timeout){requests++;try{var node=json.readTree(request);if(mode.equals("INFRA"))throw new BotInfrastructureException();if(mode.equals("BAD"))return "{}";var action=node.path("legalActions").get(0);return json.writeValueAsString(Map.of("type","ACTION","sequence",node.path("sequence").longValue(),"action",action));}catch(java.io.IOException e){throw new RuntimeException(e);}}
        public boolean isAlive(){return starts>0&&closes==0;}public void close(){closes++;}
    }
    @Test void persistentBotsUseConfiguredTurnLimitAndReplayMatchesSeededOnlinePlacement() {
        var spec=spec(8);var a=new Process();var b=new Process();var result=new HeadlessMatch().run(spec,a,b,System.nanoTime()+Duration.ofSeconds(10).toNanos());
        assertEquals("FINISHED",result.status());assertEquals(8,result.turnCount());assertEquals(1,a.starts);assertEquals(1,b.starts);assertEquals(1,a.closes);assertEquals(1,b.closes);assertTrue(a.requests>1&&b.requests>1);
        var replay=new ReplayService().replay(result.replay());assertTrue(replay.isFinished());assertEquals(result.winner(),replay.result().orElseThrow().winner().map(p->p.value()).orElse(null));assertEquals(Long.MAX_VALUE,result.replay().seed());
        var room=new OnlineMatch(spec.matchId().toString(),MatchMode.HUMAN_VS_HUMAN,spec.seed(),Clock.systemUTC(),Duration.ofSeconds(1));room.attachBot(OnlineMatch.P1,(v,l)->null,"A");room.join();room.attachBot(OnlineMatch.P2,(v,l)->null,"B");room.prepareBots();
        assertEquals(room.state().board().allPieces(),result.replay().initialState().board().allPieces());
        var again=new HeadlessMatch().run(spec,new Process(),new Process(),System.nanoTime()+Duration.ofSeconds(10).toNanos());assertEquals(result.replay().events(),again.replay().events());
    }
    @Test void protocolFaultForfeitsButInfrastructureAndHardDeadlineNeverInventWinner() {
        var a=new Process();a.mode="BAD";var b=new Process();var result=new HeadlessMatch().run(spec(200),a,b,System.nanoTime()+Duration.ofSeconds(10).toNanos());assertEquals("P2",result.winner());assertEquals("BOT_FAILED",result.reason());assertTrue(new ReplayService().replay(result.replay()).isFinished());
        a=new Process();a.mode="INFRA";var infra=a;var other=new Process();assertThrows(BotInfrastructureException.class,()->new HeadlessMatch().run(spec(200),infra,other,System.nanoTime()+Duration.ofSeconds(10).toNanos()));assertEquals(1,infra.closes);assertEquals(1,other.closes);
        var expired=new Process();var expiredB=new Process();assertThrows(IllegalStateException.class,()->new HeadlessMatch().run(spec(200),expired,expiredB,System.nanoTime()-1));assertEquals(1,expired.closes);assertEquals(1,expiredB.closes);
    }
    @Test void commandOnlyMountsArtifactAndRunsFixedSandboxedRuntimeWithoutCompilers() {
        for(String language:List.of("PYTHON","CPP","JAVA")){
            var ref=new ArtifactRef(UUID.randomUUID(),"a".repeat(64),language,Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").get(language));
            var args=SandboxBot.command(java.nio.file.Path.of("artifact.bin"),ref,"fixture",UUID.randomUUID());
            assertTrue(args.containsAll(List.of("--network=none","--read-only","--cap-drop=ALL","--user=65534:65534","--pids-limit=64","--memory=384m","--cpus=0.5","--pull=never")));assertFalse(args.getLast().contains("g++"));assertFalse(args.getLast().contains("javac"));assertFalse(args.toString().contains("docker.sock"));assertTrue(args.stream().filter(s->s.startsWith("type=bind,")).allMatch(s->s.endsWith(",readonly")));
        }
    }
}
