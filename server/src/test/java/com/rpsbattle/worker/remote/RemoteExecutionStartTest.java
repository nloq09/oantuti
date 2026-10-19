package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RemoteExecutionStartTest extends RemoteJobClaimTest {
    @Test void startIsOwnedIdempotentFencedAndKeepsSlotUntilResultCommit()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");queue.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"));var job=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();
            var body=new RemoteMatchQueue.Start(job.executionId(),machine.epoch());String path="/internal/workers/"+machine.id()+"/jobs/"+job.jobId()+"/start";
            assertThat(admin.call(path,"POST",body,null,true).status()).isEqualTo(401);assertThat(client.call(path,"POST",body,other.enrolled().workerCredential(),false).status()).isEqualTo(403);
            assertThat(client.call(path,"POST",body,machine.enrolled().workerCredential(),false).status()).isEqualTo(200);assertThat(queue.start(machine.id(),job.jobId(),body).status()).isEqualTo("RUNNING");assertThat(telemetry.view(machine.id()).reservedSlots()).isEqualTo(1);
            assertThatThrownBy(()->queue.decline(machine.id(),job.jobId(),new RemoteMatchQueue.Decline(job.executionId(),"SHUTDOWN_BEFORE_START"))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_ALREADY_STARTED");
            assertThatThrownBy(()->queue.start(other.id(),job.jobId(),new RemoteMatchQueue.Start(job.executionId(),other.epoch()))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");
            db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),job.executionId());assertThatThrownBy(()->queue.start(machine.id(),job.jobId(),body)).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_STARTABLE");
        }
    }
    @Test void staleEpochRevokedTrustAndOfflineHeartbeatCannotStart()throws Exception {
        try(var admin=new Browser()) {
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("JAVA"),1,"TRUSTED");queue.enqueue(spec(actor,"JAVA","JAVA","TRUSTED"));var job=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();var body=new RemoteMatchQueue.Start(job.executionId(),machine.epoch());
            assertThatThrownBy(()->queue.start(machine.id(),job.jobId(),new RemoteMatchQueue.Start(job.executionId(),machine.epoch()+1))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_SESSION");
            db.update("update workers set trust_level='COMMUNITY' where id=?",machine.id());assertThatThrownBy(()->queue.start(machine.id(),job.jobId(),body)).isInstanceOf(AuthException.class).hasMessage("WORKER_NOT_AVAILABLE");db.update("update workers set trust_level='TRUSTED',last_heartbeat_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(31)),machine.id());assertThatThrownBy(()->queue.start(machine.id(),job.jobId(),body)).isInstanceOf(AuthException.class).hasMessage("WORKER_NOT_AVAILABLE");assertThat(queue.assigned(machine.id()).jobs().getFirst().status()).isEqualTo("CLAIMED");
        }
    }
}
