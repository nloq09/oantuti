package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
@org.springframework.boot.test.context.SpringBootTest(webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class WorkerPoolTest extends RemoteJobClaimTest {
    @Autowired WorkerCapacityService capacity;
    @Autowired RemoteLeaseService leases;

    @Test void cancelledTournamentsReleaseBothQueuedCapacityAndActiveSlotsWithoutWaitingForLease()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var a=spec(actor,"PYTHON","PYTHON","TRUSTED");var b=spec(actor,"PYTHON","PYTHON","TRUSTED");UUID first=queue.enqueue(a),second=queue.enqueue(b);queue.claim(m.id(),claim(m,1));
            db.update("update tournaments set status='CANCELLED' where id in (?,?)",a.tournamentId(),b.tournamentId());assertThat(leases.expire()).isEqualTo(1);assertThat(telemetry.view(m.id()).reservedSlots()).isZero();for(UUID id:List.of(first,second))assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,id)).isEqualTo("CANCELLED");
        }
    }
    @Test void routedJobsUseAllApprovedMachinesAndRespectTournamentConcurrency()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var a=machine(actor,Set.of("PYTHON"),4,"TRUSTED");var b=machine(actor,Set.of("PYTHON"),4,"TRUSTED");var first=spec(actor,"PYTHON","PYTHON","TRUSTED");UUID job=queue.enqueue(first);
            db.update("insert into worker_pool_routes(job_id) values(?)",job);db.update("update tournaments set status='RUNNING' where id=?",first.tournamentId());
            db.update("insert into worker_pool_policies values(?,'AUTO','TRUSTED',1,1)",first.tournamentId());
            UUID second=UUID.randomUUID();var seats=db.queryForMap("select participant_a_id,participant_b_id from tournament_matches where id=?",first.matchId());db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",second,first.tournamentId(),seats.get("participant_a_id"),seats.get("participant_b_id"));
            var sameCup=new RemoteMatchQueue.Spec(second,first.tournamentId(),first.botA(),first.botB(),"1","classic-online-v1",1,1000,60000,200,"TRUSTED");UUID waiting=queue.enqueue(sameCup);db.update("insert into worker_pool_routes(job_id) values(?)",waiting);
            var assigned=queue.claim(b.id(),claim(b,4));assertThat(assigned.jobs()).hasSize(1);assertThat(queue.claim(a.id(),claim(a,4)).jobs()).isEmpty();
            var other=spec(actor,"PYTHON","PYTHON","TRUSTED");queue.enqueue(other);var batch=queue.claim(a.id(),claim(a,4));assertThat(batch.jobs()).hasSize(1);assertThat(batch.jobs().getFirst().match().tournamentId()).isEqualTo(other.tournamentId());
            var active=assigned.jobs().getFirst();queue.decline(b.id(),active.jobId(),new RemoteMatchQueue.Decline(active.executionId(),"EXECUTION_NOT_AVAILABLE"));assertThat(queue.claim(a.id(),claim(a,1)).jobs()).hasSize(1);
        }
    }
    @Test void saturatedTournamentCannotHideOtherTournamentsBehindTheCandidatePage()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("PYTHON"),2,"TRUSTED");var first=spec(actor,"PYTHON","PYTHON","TRUSTED");queue.enqueue(first);
            db.update("insert into worker_pool_policies values(?,'AUTO','TRUSTED',1,0)",first.tournamentId());assertThat(queue.claim(machine.id(),claim(machine,1)).jobs()).hasSize(1);
            var seats=db.queryForMap("select participant_a_id,participant_b_id from tournament_matches where id=?",first.matchId());
            for(int i=0;i<257;i++){
                UUID match=UUID.randomUUID();db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",match,first.tournamentId(),seats.get("participant_a_id"),seats.get("participant_b_id"));
                queue.enqueue(new RemoteMatchQueue.Spec(match,first.tournamentId(),first.botA(),first.botB(),"1","classic-online-v1",i,1000,60000,200,"TRUSTED"));
            }
            var other=spec(actor,"PYTHON","PYTHON","TRUSTED");queue.enqueue(other);
            var batch=queue.claim(machine.id(),claim(machine,1));assertThat(batch.jobs()).hasSize(1);assertThat(batch.jobs().getFirst().match().tournamentId()).isEqualTo(other.tournamentId());
        }
    }
    @Test void cpuMemoryAndAdminCeilingsReduceOnlyNewAdmissionAndCannotBeOverriddenByHeartbeat()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),8,"TRUSTED");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(8);
            capacity.limit(m.id(),actor,new WorkerCapacityService.Limit(1,2,"Keep desktop responsive"));assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(2);
            assertThatThrownBy(()->capacity.limit(m.id(),actor,new WorkerCapacityService.Limit(1,8,"Stale"))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_CONTROL");
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(m.epoch(),2L,0,8,0,95.0,30.0,10000L,0L),"local");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(1);
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(m.epoch(),3L,0,8,0,10.0,92.0,10000L,0L),"local");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isZero();
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(m.epoch(),4L,0,8,0,10.0,30.0,10000L,0L),"local");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(2);
        }
    }
    @Test void recentCacheHitMayGoFirstButAgedCacheMissAlwaysWins()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("CPP"),2,"TRUSTED");var miss=spec(actor,"CPP","CPP","TRUSTED");var hit=spec(actor,"CPP","CPP","TRUSTED");UUID old=queue.enqueue(miss);queue.enqueue(hit);
            var request=claim(m,1);var cached=new RemoteMatchQueue.Claim(request.requestId(),m.epoch(),1,List.of(hit.botA().artifactHash(),hit.botB().artifactHash()),"1","classic-online-v1",1);
            var got=queue.claim(m.id(),cached).jobs().getFirst();assertThat(got.match().matchId()).isEqualTo(hit.matchId());queue.decline(m.id(),got.jobId(),new RemoteMatchQueue.Decline(got.executionId(),"EXECUTION_NOT_AVAILABLE"));
            db.update("update match_execution_jobs set created_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(31)),old);
            assertThat(queue.claim(m.id(),new RemoteMatchQueue.Claim(UUID.randomUUID(),m.epoch(),1,cached.cachedArtifacts(),"1","classic-online-v1",1)).jobs().getFirst().jobId()).isEqualTo(old);
        }
    }
    @Test void individualCapacityRequiresAdminCsrfAndRejectsStaleChanges()throws Exception{
        try(var admin=new Browser();var user=new Browser()){
            UUID actor=admin.account(true);user.account(false);var m=machine(actor,Set.of("PYTHON"),4,"TRUSTED");String path="/api/admin/workers/"+m.id()+"/capacity";var change=new WorkerCapacityService.Limit(1,2,"Resource budget");
            assertThat(user.call(path,"POST",change,null,true).status()).isEqualTo(403);assertThat(admin.call(path,"POST",change,null,false).status()).isEqualTo(403);assertThat(admin.call(path,"POST",change,null,true).status()).isEqualTo(200);assertThat(admin.call(path,"POST",change,null,true).status()).isEqualTo(409);
            assertThat(admin.call("/api/admin/worker-groups","GET",null,null,false).status()).isEqualTo(404);
        }
    }
}
