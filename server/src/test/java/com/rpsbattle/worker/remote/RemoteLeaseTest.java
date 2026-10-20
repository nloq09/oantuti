package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class RemoteLeaseTest extends RemoteResultTest {
    @Autowired RemoteLeaseService leases;
    void expired(UUID execution,int seconds){db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(seconds)),execution);}
    @Test void renewalRequiresOwnedFreshEpochAndNeverRevivesExpiredOrUntrustedExecution()throws Exception {
        try(var admin=new Browser();var client=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);var machine=f.machine();UUID execution=f.job().executionId();var body=new RemoteLeaseService.Renew(machine.epoch(),List.of(execution));
            db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().plusSeconds(5)),execution);
            var renewed=leases.renew(machine.id(),body).executions().getFirst();assertThat(renewed.status()).isEqualTo("ACTIVE");assertThat(renewed.leaseUntil()).isAfter(Instant.now().plusSeconds(20));
            String path="/internal/workers/"+machine.id()+"/jobs/renew";assertThat(admin.call(path,"POST",body,null,true).status()).isEqualTo(401);var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");assertThat(client.call(path,"POST",body,other.enrolled().workerCredential(),false).status()).isEqualTo(403);
            assertThatThrownBy(()->leases.renew(other.id(),new RemoteLeaseService.Renew(other.epoch(),List.of(execution)))) .isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");assertThatThrownBy(()->leases.renew(machine.id(),new RemoteLeaseService.Renew(machine.epoch()+1,List.of(execution)))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_SESSION");
            db.update("update workers set trust_level='COMMUNITY' where id=?",machine.id());assertThat(leases.renew(machine.id(),body).executions().getFirst().status()).isEqualTo("LEASE_LOST");db.update("update workers set trust_level='TRUSTED' where id=?",machine.id());expired(execution,1);assertThat(leases.renew(machine.id(),body).executions().getFirst().status()).isEqualTo("LEASE_LOST");assertThat(leases.expire()).isZero();assertThat(telemetry.view(machine.id()).reservedSlots()).isEqualTo(1);
        }
    }
    @Test void completedResultWithinGraceRecoversSameExecutionAndOldResultAfterReassignmentCannotCommit()throws Exception {
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);expired(f.job().executionId(),1);
            var grant=leases.recover(f.machine().id(),new RemoteLeaseService.Recovery(f.job().executionId(),"RESULT_READY"));assertThat(grant.status()).isEqualTo("UPLOAD_ALLOWED");assertThat(grant.leaseUntil()).isAfter(Instant.now());results.accept(f.machine().id(),f.job().jobId(),f.upload());assertThat(leases.recover(f.machine().id(),new RemoteLeaseService.Recovery(f.job().executionId(),"RESULT_READY")).status()).isEqualTo("DONE");
            var next=fixture(actor);expired(next.job().executionId(),31);assertThat(leases.expire()).isEqualTo(1);var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var reassigned=queue.claim(other.id(),claim(other,1)).jobs().getFirst();assertThat(reassigned.jobId()).isEqualTo(next.job().jobId());assertThat(reassigned.executionId()).isNotEqualTo(next.job().executionId());
            assertThat(leases.recover(next.machine().id(),new RemoteLeaseService.Recovery(next.job().executionId(),"RESULT_READY")).status()).isEqualTo("SUPERSEDED");assertThatThrownBy(()->results.accept(next.machine().id(),next.job().jobId(),next.upload())).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_COMPLETABLE");assertThat(telemetry.view(next.machine().id()).reservedSlots()).isZero();assertThat(telemetry.view(other.id()).reservedSlots()).isEqualTo(1);
        }
    }
    @Test void restartAbandonIsIdempotentAndThreeInfrastructureFailuresNeverChooseWinner()throws Exception {
        try(var admin=new Browser()){
            var f=fixture(admin.account(true));var execution=f.job();
            for(int attempt=1;attempt<=3;attempt++){
                var body=new RemoteLeaseService.Recovery(execution.executionId(),"ABANDON");assertThat(leases.recover(f.machine().id(),body).status()).isEqualTo("ABANDONED");leases.recover(f.machine().id(),body);assertThat(telemetry.view(f.machine().id()).reservedSlots()).isZero();
                assertThat(db.queryForObject("select count(*) from worker_execution_recoveries where execution_id=?",Integer.class,execution.executionId())).isEqualTo(1);
                if(attempt<3){execution=queue.claim(f.machine().id(),claim(f.machine(),1)).jobs().getFirst();queue.start(f.machine().id(),execution.jobId(),new RemoteMatchQueue.Start(execution.executionId(),f.machine().epoch()));}
            }
            assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,f.job().jobId())).isEqualTo("CANCELLED");projector.projectFailure(execution.executionId());projector.projectFailure(execution.executionId());var match=f.tournament().matches.getFirst();assertThat(match.status).isEqualTo(com.rpsbattle.tournament.domain.TournamentMatch.Status.FAILED);assertThat(match.result).isNull();assertThat(match.winnerParticipantId).isNull();assertThat(match.failure).isEqualTo("REMOTE_WORKER_INFRASTRUCTURE_ERROR");assertThat(f.tournament().operations.audit().stream().filter(e->e.reason()!=null&&e.reason().equals("REMOTE_RETRY_EXHAUSTED"))).hasSize(1);
        }
    }
    @Test void concurrentExpiryAndRecoveryCreateOneRecoveryAndRenewalHasHardUpperBound()throws Exception {
        try(var admin=new Browser();var pool=Executors.newFixedThreadPool(4)){
            var f=fixture(admin.account(true));expired(f.job().executionId(),31);var tasks=new ArrayList<Future<?>>();tasks.add(pool.submit(()->leases.recover(f.machine().id(),new RemoteLeaseService.Recovery(f.job().executionId(),"RESULT_READY"))));for(int i=0;i<3;i++)tasks.add(pool.submit(()->leases.expire()));for(var task:tasks)task.get(10,TimeUnit.SECONDS);assertThat(db.queryForObject("select count(*) from worker_execution_recoveries where execution_id=?",Integer.class,f.job().executionId())).isEqualTo(1);
            var g=fixture(admin.account(true));db.update("update match_execution_attempts set started_at=?,lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(200)),java.sql.Timestamp.from(Instant.now().plusSeconds(20)),g.job().executionId());assertThat(leases.renew(g.machine().id(),new RemoteLeaseService.Renew(g.machine().epoch(),List.of(g.job().executionId()))).executions().getFirst().status()).isEqualTo("LEASE_LOST");assertThat(leases.recover(g.machine().id(),new RemoteLeaseService.Recovery(g.job().executionId(),"RESULT_READY")).status()).isEqualTo("SUPERSEDED");
        }
    }
}
