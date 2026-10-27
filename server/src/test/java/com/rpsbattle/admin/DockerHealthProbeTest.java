package com.rpsbattle.admin;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class DockerHealthProbeTest {
    @Test void unavailableRuntimeIsCachedAndDoesNotInventAContainerCount(){var probe=new DockerHealthProbe("definitely-missing-rps-docker-executable","local");try{assertThat(probe.status().get("status")).isEqualTo("UNKNOWN");probe.refresh();var value=probe.status();assertThat(value.get("status")).isEqualTo("DOWN");assertThat(value.get("managedContainers")).isNull();assertThat(probe.status()).isSameAs(value);assertThat(value.toString()).doesNotContain("docker.sock","exception","stack");}finally{probe.close();}}
    @Test void requestsNeverWaitForSlowSamplerOrLaunchAnotherDockerCheck() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var probe=new DockerHealthProbe(()->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return Map.of("status","UP","managedContainers",3L);});
        try {
            probe.start();assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                for(int i=0;i<100;i++){assertThat(probe.status().get("status")).isEqualTo("UNKNOWN");assertThat(probe.status().get("managedContainers")).isNull();}
            });
            assertThat(calls.get()).isEqualTo(1);release.countDown();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(()->assertThat(probe.status().get("status")).isEqualTo("UP"));
            assertThat(probe.status().get("managedContainers")).isEqualTo(3L);
        } finally {release.countDown();probe.close();}
    }
    @Test void failedBackgroundRefreshReplacesOldHealthySampleWithoutLeakingFailureDetails(){
        var calls=new AtomicInteger();var probe=new DockerHealthProbe(()->{if(calls.getAndIncrement()==0)return Map.of("status","UP","managedContainers",1L);throw new IllegalStateException("private Docker path");});
        try{probe.refresh();assertThat(probe.status().get("status")).isEqualTo("UP");probe.refresh();assertThat(probe.status().get("status")).isEqualTo("DOWN");assertThat(probe.status().get("managedContainers")).isNull();assertThat(probe.status().toString()).doesNotContain("private Docker path");}finally{probe.close();}
    }
}
