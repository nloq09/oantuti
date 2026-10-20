package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class WorkerControlTest extends RemoteResultTest {
    @Autowired WorkerControlService controls;
    @Autowired RemoteLeaseService leases;
    WorkerTelemetryService.View command(UUID worker,UUID actor,String action){return controls.control(worker,actor,new WorkerControlService.Command(telemetry.view(worker).revision(),action,"Operator maintenance"));}
    @Test void drainPreservesOwnedExecutionAndResultButCannotClaimOrResetDrainOnRestart()throws Exception {
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);var m=f.machine();
            assertThat(command(m.id(),actor,"DRAIN").status()).isEqualTo("DRAINING");
            assertThatThrownBy(()->queue.claim(m.id(),claim(m,1))).isInstanceOf(AuthException.class).hasMessage("WORKER_NOT_AVAILABLE");
            db.update("update match_execution_attempts set status='CLAIMED' where id=?",f.job().executionId());
            assertThat(queue.start(m.id(),f.job().jobId(),new RemoteMatchQueue.Start(f.job().executionId(),m.epoch())).status()).isEqualTo("RUNNING");
            assertThat(leases.renew(m.id(),new RemoteLeaseService.Renew(m.epoch(),List.of(f.job().executionId()))).executions().getFirst().status()).isEqualTo("ACTIVE");
            results.accept(m.id(),f.job().jobId(),f.upload());assertThat(telemetry.view(m.id()).status()).isEqualTo("DRAINED");
            long epoch=telemetry.capabilities(m.id(),new WorkerTelemetryService.Capabilities(8,16000L,"Linux",true,Set.of("PYTHON"),1,2,"0.7.0",1)).epoch();
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(epoch,1L,0,1,0,0.0,0.0,0L,0L),"local");
            assertThat(telemetry.view(m.id()).status()).isEqualTo("DRAINED");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isZero();
        }
    }
    @Test void disableFencesOldExecutionAndResumeRequiresFreshSessionWithoutInventingWinner()throws Exception {
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);var m=f.machine();
            assertThat(command(m.id(),actor,"DISABLE").reservedSlots()).isZero();
            assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,f.job().jobId())).isEqualTo("QUEUED");
            assertThatThrownBy(()->results.accept(m.id(),f.job().jobId(),f.upload())).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_COMPLETABLE");
            assertThatThrownBy(()->identities.authenticate(m.enrolled().workerCredential())).isInstanceOf(AuthException.class);
            assertThat(command(m.id(),actor,"RESUME").status()).isEqualTo("STARTING");
            assertThat(identities.authenticate(m.enrolled().workerCredential())).isEqualTo(m.id());
            assertThatThrownBy(()->queue.claim(m.id(),claim(m,1))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_SESSION");
            assertThat(f.tournament().matches.getFirst().winnerParticipantId).isNull();assertThat(f.tournament().matches.getFirst().result).isNull();
            assertThat(controls.events(m.id())).anySatisfy(e->{assertThat(e.get("event_type")).isEqualTo("WORKER_DISABLE");assertThat(e.get("reason")).isEqualTo("Operator maintenance");});
        }
    }
    @Test void revokeImmediatelyBlocksEveryWorkerRouteAndCannotBeResumed()throws Exception {
        try(var admin=new Browser();var client=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);var m=f.machine();String base="/internal/workers/"+m.id();
            assertThat(command(m.id(),actor,"REVOKE").credentialRevoked()).isTrue();
            for(String route:List.of("identity","jobs/assigned","artifacts/"+UUID.randomUUID()+"?executionId="+f.job().executionId()))assertThat(client.call(base+"/"+route,"GET",null,m.enrolled().workerCredential(),false).status()).isEqualTo(401);
            for(String route:List.of("heartbeat","capabilities","jobs/claim","jobs/renew","jobs/recover","jobs/"+f.job().jobId()+"/complete"))assertThat(client.call(base+"/"+route,"POST",Map.of(),m.enrolled().workerCredential(),false).status()).isEqualTo(401);
            assertThatThrownBy(()->command(m.id(),actor,"RESUME")).isInstanceOf(AuthException.class).hasMessage("WORKER_CONTROL_CONFLICT");assertThat(telemetry.view(m.id()).reservedSlots()).isZero();
            assertThat(controls.events(m.id()).toString()).doesNotContain(m.enrolled().workerCredential(),"credential_hash");
        }
    }
    @Test void controlsRequireAdminCsrfReasonAndRevisionAndAuditStaleActionOnlyOnce()throws Exception {
        try(var admin=new Browser();var user=new Browser();var guest=new Browser()){
            UUID actor=admin.account(true);user.account(false);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");String path="/api/admin/workers/"+m.id()+"/control";var body=new WorkerControlService.Command(1,"DRAIN","Scheduled maintenance");
            assertThat(guest.call(path,"POST",body,null,true).status()).isEqualTo(401);assertThat(user.call(path,"POST",body,null,true).status()).isEqualTo(403);assertThat(admin.call(path,"POST",body,null,false).status()).isEqualTo(403);
            assertThat(guest.call(path,"POST",body,m.enrolled().workerCredential(),true).status()).isEqualTo(401);
            assertThat(admin.call(path,"POST",new WorkerControlService.Command(1,"DRAIN"," "),null,true).status()).isEqualTo(400);
            assertThat(admin.call(path,"POST",body,null,true).status()).isEqualTo(200);assertThat(admin.call(path,"POST",body,null,true).status()).isEqualTo(409);
            assertThat(db.queryForObject("select count(*) from worker_identity_events where resource_id=? and event_type='WORKER_DRAIN'",Integer.class,m.id())).isEqualTo(1);
            assertThat(user.call(path.replace("/control","/events"),"GET",null,null,false).status()).isEqualTo(403);assertThat(admin.call(path.replace("/control","/events"),"GET",null,null,false).status()).isEqualTo(200);
        }
    }
    @Test void concurrentClaimAndDisableCannotLeaveNewReservationsOrDuplicateRecovery()throws Exception {
        try(var admin=new Browser();var pool=Executors.newFixedThreadPool(2)){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");queue.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"));var gate=new CountDownLatch(1);
            var claim=pool.submit(()->{gate.await();try{queue.claim(m.id(),claim(m,1));}catch(AuthException e){assertThat(e.code()).isIn("WORKER_CREDENTIAL_INVALID","STALE_WORKER_SESSION");}return true;});
            var disable=pool.submit(()->{gate.await();command(m.id(),actor,"DISABLE");return true;});gate.countDown();claim.get(15,TimeUnit.SECONDS);disable.get(15,TimeUnit.SECONDS);
            assertThat(telemetry.view(m.id()).reservedSlots()).isZero();assertThat(db.queryForObject("select count(*) from worker_execution_recoveries r join match_execution_attempts a on a.id=r.execution_id where a.worker_id=?",Integer.class,m.id())).isLessThanOrEqualTo(1);
        }
    }
}
