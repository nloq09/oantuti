package com.rpsbattle.worker.remote;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
class WorkerMetricsTest extends RemoteResultTest {
    @Autowired WorkerMetricsService metrics;
    @Test void metricsAreAdminOnlyAndReportActualReceiptsWaitsAndCacheCounters()throws Exception{
        try(var admin=new Browser();var user=new Browser();var guest=new Browser()){
            UUID actor=admin.account(true);user.account(false);var f=fixture(actor);var before=metrics.read(null);
            telemetry.heartbeat(f.machine().id(),new WorkerTelemetryService.Heartbeat(f.machine().epoch(),2L,1,0,2,40.0,30.0,10000L,1L,12L,3L),"local");results.accept(f.machine().id(),f.job().jobId(),f.upload());
            var scoped=metrics.read(UUID.fromString(f.tournament().id));assertThat(scoped.acceptedResults()).isEqualTo(1);assertThat(scoped.receiptsLast60Seconds()).isEqualTo(1);assertThat(scoped.totalAttempts()).isEqualTo(1);assertThat(scoped.waitSampleCount()).isEqualTo(1);assertThat(scoped.leasedJobs()).isZero();assertThat(scoped.cacheHits()-before.cacheHits()).isEqualTo(12);assertThat(scoped.cacheMisses()-before.cacheMisses()).isEqualTo(3);
            String path="/api/admin/workers/metrics?tournamentId="+f.tournament().id;assertThat(user.call(path,"GET",null,null,false).status()).isEqualTo(403);assertThat(guest.call(path,"GET",null,null,false).status()).isEqualTo(401);var response=admin.call(path,"GET",null,null,false);assertThat(response.status()).isEqualTo(200);assertThat(response.body().toString()).doesNotContain(f.machine().enrolled().workerCredential(),"credential_hash");
            assertThatThrownBy(()->telemetry.heartbeat(f.machine().id(),new WorkerTelemetryService.Heartbeat(f.machine().epoch(),3L,0,1,0,0.0,0.0,0L,0L,Long.MAX_VALUE,0L),"local")).hasMessage("INVALID_WORKER_HEARTBEAT");
        }
    }
    @Test void percentilesUseNearestRankAndEmptySamplesStayExplicit(){assertThat(WorkerMetricsService.percentile(List.of(),.99)).isZero();assertThat(WorkerMetricsService.percentile(List.of(1L,100L,2L,50L),.5)).isEqualTo(2);assertThat(WorkerMetricsService.percentile(List.of(1L,100L,2L,50L),.95)).isEqualTo(100);}
}
