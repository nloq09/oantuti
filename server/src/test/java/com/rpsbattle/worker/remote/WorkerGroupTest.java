package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
@org.springframework.boot.test.context.SpringBootTest(webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class WorkerGroupTest extends RemoteJobClaimTest {
    @Autowired WorkerGroupService groups;
    @Autowired RemoteLeaseService leases;
    WorkerGroupService.Group group(UUID actor,Machine m){var g=groups.create("LAB_"+UUID.randomUUID().toString().substring(0,8),actor,"Group fixture");return groups.change(g.id(),actor,new WorkerGroupService.Change(g.revision(),null,m.id(),true,"Approved membership"));}
    @Test void cancelledTournamentsReleaseBothQueuedCapacityAndActiveSlotsWithoutWaitingForLease()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var a=spec(actor,"PYTHON","PYTHON","TRUSTED");var b=spec(actor,"PYTHON","PYTHON","TRUSTED");UUID first=queue.enqueue(a),second=queue.enqueue(b);queue.claim(m.id(),claim(m,1));
            db.update("update tournaments set status='CANCELLED' where id in (?,?)",a.tournamentId(),b.tournamentId());assertThat(leases.expire()).isEqualTo(1);assertThat(telemetry.view(m.id()).reservedSlots()).isZero();for(UUID id:List.of(first,second))assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,id)).isEqualTo("CANCELLED");
        }
    }
    @Test void routedJobsStayInsideEnabledGroupAndRespectTournamentConcurrency()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),4,"TRUSTED");var other=machine(actor,Set.of("PYTHON"),4,"TRUSTED");var g=group(actor,m);var first=spec(actor,"PYTHON","PYTHON","TRUSTED");UUID job=queue.enqueue(first);
            db.update("insert into remote_job_routes(job_id,group_id) values(?,?)",job,g.id());db.update("update tournaments set status='RUNNING' where id=?",first.tournamentId());
            db.update("insert into tournament_worker_policies values(?,'REMOTE_REQUIRED',?,'TRUSTED',1,1)",first.tournamentId(),g.id());
            assertThat(queue.claim(other.id(),claim(other,4)).jobs()).isEmpty();
            groups.change(g.id(),actor,new WorkerGroupService.Change(g.revision(),false,null,null,"Maintenance"));assertThat(queue.claim(m.id(),claim(m,4)).jobs()).isEmpty();
            var current=groups.group(g.id());groups.change(g.id(),actor,new WorkerGroupService.Change(current.revision(),true,null,null,"Ready"));assertThat(queue.claim(m.id(),claim(m,4)).jobs()).hasSize(1);
            assertThat(telemetry.view(m.id()).reservedSlots()).isEqualTo(1);
        }
    }
    @Test void cpuMemoryAndAdminCeilingsReduceOnlyNewAdmissionAndCannotBeOverriddenByHeartbeat()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),8,"TRUSTED");assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(8);
            groups.limit(m.id(),actor,new WorkerGroupService.Limit(1,2,"Keep desktop responsive"));assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isEqualTo(2);
            assertThatThrownBy(()->groups.limit(m.id(),actor,new WorkerGroupService.Limit(1,8,"Stale"))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_CONTROL");
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
    @Test void groupsAndCapacityRequireAdminCsrfAndRejectStaleOrMalformedChanges()throws Exception{
        try(var admin=new Browser();var user=new Browser()){
            UUID actor=admin.account(true);user.account(false);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var g=group(actor,m);String path="/api/admin/worker-groups/"+g.id();var change=new WorkerGroupService.Change(g.revision(),false,null,null,"Maintenance");
            assertThat(user.call(path,"POST",change,null,true).status()).isEqualTo(403);assertThat(admin.call(path,"POST",change,null,false).status()).isEqualTo(403);assertThat(admin.call(path,"POST",change,null,true).status()).isEqualTo(200);assertThat(admin.call(path,"POST",change,null,true).status()).isEqualTo(409);
            assertThatThrownBy(()->groups.create("bad/name",actor,"Invalid")).isInstanceOf(AuthException.class);assertThat(user.call("/api/admin/workers/"+m.id()+"/capacity","POST",new WorkerGroupService.Limit(1,1,"Unauthorized"),null,true).status()).isEqualTo(403);
        }
    }
}
