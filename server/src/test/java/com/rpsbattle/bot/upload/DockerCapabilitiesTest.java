package com.rpsbattle.bot.upload;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DockerCapabilitiesTest {
 @Test void cachesDaemonAndEachDistinctImageOnceIncludingSharedJavaRuntime(){
  var calls=new ArrayList<List<String>>();var time=new AtomicLong();var probe=new DockerCapabilities((cmd,expected)->{calls.add(cmd);return true;},time::get);
  for(int i=0;i<20;i++)for(var language:BotLanguage.values())assertThat(probe.available(language)).isTrue();
  assertThat(calls).hasSize(4);assertThat(calls.stream().filter(cmd->cmd.contains("eclipse-temurin:21-jdk"))).hasSize(1);
 }
 @Test void daemonFailureIsCachedAndRecoversAfterTheBoundedNegativeTtl(){
  var calls=new AtomicInteger();var time=new AtomicLong();var ready=new AtomicBoolean();var probe=new DockerCapabilities((cmd,expected)->{calls.incrementAndGet();return ready.get();},time::get);
  for(int i=0;i<100;i++)assertThat(probe.available(BotLanguage.PYTHON)).isFalse();assertThat(calls.get()).isEqualTo(1);
  ready.set(true);time.set(TimeUnit.SECONDS.toNanos(5)-1);assertThat(probe.available(BotLanguage.PYTHON)).isFalse();time.incrementAndGet();assertThat(probe.available(BotLanguage.PYTHON)).isTrue();assertThat(calls.get()).isEqualTo(5);
 }
 @Test void missingImageIsCachedWithoutHidingOtherLanguages(){
  var calls=new AtomicInteger();var probe=new DockerCapabilities((cmd,expected)->{calls.incrementAndGet();return !cmd.contains("gcc:14");},()->0);
  assertThat(probe.available(BotLanguage.CPP)).isFalse();assertThat(probe.available(BotLanguage.JAVA)).isTrue();assertThat(probe.available(BotLanguage.JAR)).isTrue();assertThat(calls.get()).isEqualTo(4);
 }
 @Test void concurrentRequestsDoNotSpawnDuplicateProbesOrWaitForTheExistingFlight()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
  var probe=new DockerCapabilities((cmd,expected)->{calls.incrementAndGet();if(cmd.contains("info")){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("probe not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}return true;},()->0);
  try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->probe.available(BotLanguage.PYTHON));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();try{for(int i=0;i<100;i++)assertThat(pool.submit(()->probe.available(BotLanguage.PYTHON)).get(1,TimeUnit.SECONDS)).isFalse();assertThat(calls.get()).isEqualTo(1);}finally{release.countDown();}assertThat(first.get(2,TimeUnit.SECONDS)).isTrue();assertThat(calls.get()).isEqualTo(4);}
 }
}
