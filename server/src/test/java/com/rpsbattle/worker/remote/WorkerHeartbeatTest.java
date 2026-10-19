package com.rpsbattle.worker.remote;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class WorkerHeartbeatTest extends WorkerRegistrationTest {
    @Autowired WorkerTelemetryService telemetry;
    WorkerTelemetryService.Capabilities caps(boolean docker,Set<String> languages){return new WorkerTelemetryService.Capabilities(8,16384L,"Linux",docker,languages,4,8,"0.2.0",1);}
    WorkerIdentityService.Enrolled enroll(UUID actor){return identities.register(registration(identities.issue(actor,1,15).registrationToken()));}
    WorkerTelemetryService.Heartbeat heartbeat(long epoch,long sequence,int active,int free,int bots){return new WorkerTelemetryService.Heartbeat(epoch,sequence,active,free,bots,62.3,54.1,120000L,850L);}
    @Test void capabilityAndHeartbeatAreBoundToWorkerCredentialAndServerReceivesMetrics()throws Exception {
        try(var admin=new Browser();var machine=new Browser()) {
            var worker=enroll(admin.account(true));UUID id=worker.worker().workerId();String route="/internal/workers/"+id;
            assertThat(admin.call(route+"/capabilities","POST",caps(true,Set.of("CPP","PYTHON")),null,true).status()).isEqualTo(401);
            assertThat(machine.call("/internal/workers/"+UUID.randomUUID()+"/capabilities","POST",caps(true,Set.of("CPP")),worker.workerCredential(),false).status()).isEqualTo(403);
            var response=machine.call(route+"/capabilities","POST",caps(true,Set.of("CPP","PYTHON")),worker.workerCredential(),false);assertThat(response.status()).isEqualTo(200);long epoch=response.body().path("epoch").asLong();
            response=machine.call(route+"/heartbeat","POST",heartbeat(epoch,1,1,3,2),worker.workerCredential(),false);assertThat(response.status()).isEqualTo(200);var body=response.body();
            assertThat(body.path("status").asText()).isEqualTo("ONLINE");assertThat(body.path("effectiveFreeSlots").asInt()).isZero();assertThat(body.path("heartbeat").path("cpuUsage").asDouble()).isEqualTo(62.3);assertThat(body.path("lastHeartbeatAt").asText()).isNotBlank();
            assertThat(body.toString()).doesNotContain(worker.workerCredential(),"credential_hash","token_hash");
            assertThat(admin.call("/api/admin/workers/"+id,"GET",null,null,false).status()).isEqualTo(200);
            assertThat(machine.call("/api/admin/workers","GET",null,worker.workerCredential(),false).status()).isEqualTo(401);
        }
    }
    @Test void reviewRequiresAdminCsrfReasonAndFreshRevisionAndProducesAudit()throws Exception {
        try(var admin=new Browser();var ordinary=new Browser()) {
            UUID actor=admin.account(true);ordinary.account(false);var w=enroll(actor);UUID id=w.worker().workerId();long epoch=telemetry.capabilities(id,caps(true,Set.of("JAVA"))).epoch();telemetry.heartbeat(id,heartbeat(epoch,1,1,3,6),"127.0.0.1");
            String route="/api/admin/workers/"+id+"/review";var review=new WorkerTelemetryService.Review(0,"APPROVE","TRUSTED","Trusted lab machine");
            assertThat(ordinary.call(route,"POST",review,null,true).status()).isEqualTo(403);assertThat(admin.call(route,"POST",review,null,false).status()).isEqualTo(403);
            assertThat(admin.call(route,"POST",new WorkerTelemetryService.Review(0,"APPROVE","TRUSTED"," "),null,true).status()).isEqualTo(400);
            assertThat(admin.call(route,"POST",new WorkerTelemetryService.Review(1,"APPROVE","TRUSTED","Stale"),null,true).status()).isEqualTo(409);
            var approved=admin.call(route,"POST",review,null,true);assertThat(approved.status()).isEqualTo(200);assertThat(approved.body().path("effectiveFreeSlots").asInt()).isEqualTo(1);assertThat(approved.body().path("trustLevel").asText()).isEqualTo("TRUSTED");
            assertThat(admin.call(route,"POST",review,null,true).status()).isEqualTo(409);
            assertThat(db.queryForObject("select count(*) from worker_identity_events where resource_id=? and event_type='WORKER_APPROVED' and reason='Trusted lab machine'",Integer.class,id)).isEqualTo(1);
            assertThat(telemetry.view(id).revision()).isEqualTo(1);
        }
    }
    @Test void lostHeartbeatTurnsOfflineAtThirtySecondsAndDuplicatePacketsDoNotRefreshIt()throws Exception {
        try(var admin=new Browser()) {
            var w=enroll(admin.account(true));UUID id=w.worker().workerId();long epoch=telemetry.capabilities(id,caps(true,Set.of("CPP"))).epoch();
            telemetry.heartbeat(id,heartbeat(epoch,5,0,4,0),"192.168.0.2");Instant last=telemetry.view(id).lastHeartbeatAt();
            var before=new WorkerTelemetryService(db,Clock.fixed(last.plusSeconds(30).minusNanos(1),ZoneOffset.UTC));assertThat(before.view(id).status()).isEqualTo("ONLINE");
            var expired=new WorkerTelemetryService(db,Clock.fixed(last.plusSeconds(30),ZoneOffset.UTC));expired.expireOffline();assertThat(expired.view(id).status()).isEqualTo("OFFLINE");assertThat(expired.view(id).effectiveFreeSlots()).isZero();
            telemetry.heartbeat(id,heartbeat(epoch,4,0,4,0),"other");assertThat(telemetry.view(id).lastHeartbeatAt()).isEqualTo(last);assertThat(telemetry.view(id).heartbeat().sequence()).isEqualTo(5);
            telemetry.heartbeat(id,heartbeat(epoch,6,0,4,0),"192.168.0.2");assertThat(telemetry.view(id).status()).isEqualTo("ONLINE");
        }
    }
    @Test void restartFencesOldSessionAndInvalidCapabilitiesNeverResetCurrentHeartbeat()throws Exception {
        try(var admin=new Browser()) {
            var w=enroll(admin.account(true));UUID id=w.worker().workerId();long old=telemetry.capabilities(id,caps(true,Set.of("PYTHON"))).epoch();telemetry.heartbeat(id,heartbeat(old,1,0,4,0),"local");
            var incompatible=new WorkerTelemetryService.Capabilities(8,16000L,"Linux",true,Set.of("JAVA"),4,8,"0.2.0",99);
            assertThatThrownBy(()->telemetry.capabilities(id,incompatible)).isInstanceOf(com.rpsbattle.auth.AuthException.class);assertThat(telemetry.view(id).heartbeat().epoch()).isEqualTo(old);
            long next=telemetry.capabilities(id,caps(true,Set.of("CPP"))).epoch();assertThat(next).isGreaterThan(old);
            assertThatThrownBy(()->telemetry.heartbeat(id,heartbeat(old,9,0,4,0),"local")).isInstanceOf(com.rpsbattle.auth.AuthException.class);assertThat(telemetry.view(id).heartbeat()).isNull();
            telemetry.heartbeat(id,heartbeat(next,1,0,4,0),"local");assertThat(telemetry.view(id).heartbeat().sequence()).isEqualTo(1);
        }
    }
    @Test void missingDockerIsDegradedAndRejectedWorkerCannotHeartbeatOrAuthenticate()throws Exception {
        try(var admin=new Browser()) {
            UUID actor=admin.account(true);var w=enroll(actor);UUID id=w.worker().workerId();long epoch=telemetry.capabilities(id,caps(false,Set.of())).epoch();telemetry.heartbeat(id,heartbeat(epoch,1,0,0,0),"local");assertThat(telemetry.view(id).status()).isEqualTo("DEGRADED");
            telemetry.review(id,actor,new WorkerTelemetryService.Review(0,"REJECT",null,"Unverified external machine"));
            assertThatThrownBy(()->identities.authenticate(w.workerCredential())).isInstanceOf(com.rpsbattle.auth.AuthException.class);
            assertThatThrownBy(()->telemetry.heartbeat(id,heartbeat(epoch,2,0,0,0),"local")).isInstanceOf(com.rpsbattle.auth.AuthException.class);
        }
    }
    @Test void invalidMetricsAreRejectedWithoutCorruptingLastGoodSampleAndListIsBounded()throws Exception {
        try(var admin=new Browser()) {
            var w=enroll(admin.account(true));UUID id=w.worker().workerId();long epoch=telemetry.capabilities(id,caps(true,Set.of("CPP"))).epoch();telemetry.heartbeat(id,heartbeat(epoch,1,0,4,0),"local");
            assertThatThrownBy(()->telemetry.heartbeat(id,heartbeat(epoch,2,2,3,2),"local")).isInstanceOf(com.rpsbattle.auth.AuthException.class);
            assertThatThrownBy(()->telemetry.heartbeat(id,new WorkerTelemetryService.Heartbeat(epoch,2L,0,4,0,Double.NaN,0.0,0L,0L),"local")).isInstanceOf(com.rpsbattle.auth.AuthException.class);
            assertThat(telemetry.view(id).heartbeat().sequence()).isEqualTo(1);
            assertThat(admin.call("/api/admin/workers?size=101","GET",null,null,false).status()).isEqualTo(400);
            var page=admin.call("/api/admin/workers?size=1","GET",null,null,false);assertThat(page.body().path("items").size()).isEqualTo(1);assertThat(page.body().toString()).doesNotContain(w.workerCredential(),"credential_hash");
            var download=admin.http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/admin/workers/download")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());assertThat(download.statusCode()).isEqualTo(200);assertThat(download.headers().firstValue("Content-Type")).hasValue("application/octet-stream");assertThat(download.body()).startsWith((byte)'P',(byte)'K');
        }
    }
}
