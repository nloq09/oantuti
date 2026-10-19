package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class RemoteJobClaimTest extends WorkerRegistrationTest {
    @org.junit.jupiter.api.BeforeEach void clearIsolatedTestQueue(){for(String table:List.of("worker_claim_items","worker_claim_receipts","match_execution_attempts","match_execution_jobs"))db.update("delete from "+table);}
    @Autowired RemoteMatchQueue queue;
    @Autowired WorkerTelemetryService telemetry;
    record Machine(WorkerIdentityService.Enrolled enrolled,long epoch) {UUID id(){return enrolled.worker().workerId();}}
    Machine machine(UUID actor,Set<String> languages,int slots,String trust) {
        var enrolled=identities.register(registration(identities.issue(actor,1,15).registrationToken()));UUID id=enrolled.worker().workerId();long epoch=telemetry.capabilities(id,new WorkerTelemetryService.Capabilities(8,16000L,"Linux",true,languages,slots,slots*2,"0.3.0",1)).epoch();
        telemetry.heartbeat(id,new WorkerTelemetryService.Heartbeat(epoch,1L,0,slots,0,20.0,30.0,10000L,0L),"local");if(trust!=null)telemetry.review(id,actor,new WorkerTelemetryService.Review(0,"APPROVE",trust,"Claim fixture"));return new Machine(enrolled,epoch);
    }
    RemoteMatchQueue.Spec spec(UUID actor,String a,String b,String trust) {
        UUID cup=UUID.randomUUID(),match=UUID.randomUUID();db.update("insert into tournaments(id,owner_user_id,name,format,status) values(?,?,'Remote queue fixture','ROUND_ROBIN','DRAFT')",cup,actor);var participants=new ArrayList<UUID>();
        for(int i=0;i<2;i++){UUID bot=UUID.randomUUID(),p=UUID.randomUUID();db.update("insert into bots(id,owner_user_id,name,language,source_path,status) values(?,?,'Remote fixture','PYTHON','private-fixture','READY')",bot,actor);db.update("insert into tournament_participants(id,tournament_id,bot_id,owner_user_id,display_name,seed) values(?,?,?,?,'Remote fixture',?)",p,cup,bot,actor,i);participants.add(p);}
        db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",match,cup,participants.get(0),participants.get(1));
        return new RemoteMatchQueue.Spec(match,cup,bot(a),bot(b),"1","classic-online-v1",Long.MAX_VALUE,1000,60000,200,trust);
    }
    RemoteMatchQueue.BotRef bot(String language){return new RemoteMatchQueue.BotRef(UUID.randomUUID(),WorkerIdentityService.hash(UUID.randomUUID().toString()),language,Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").get(language));}
    RemoteMatchQueue.Claim claim(Machine m,int slots){return new RemoteMatchQueue.Claim(UUID.randomUUID(),m.epoch(),slots,List.of(),"1","classic-online-v1",1);}
    @Test void batchReservesCentralSlotsAndRetryReturnsSameExecutionsWithoutDoubleAllocating()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),2,"TRUSTED");for(int i=0;i<3;i++)queue.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"));
            var request=claim(m,256);assertThat(client.call("/internal/workers/"+m.id()+"/jobs/claim","POST",request,m.enrolled().workerCredential(),false).status()).isEqualTo(200);var batch=queue.claim(m.id(),request);assertThat(batch.jobs()).hasSize(2);assertThat(batch.occupiedSlots()).isEqualTo(2);assertThat(batch.effectiveFreeSlots()).isZero();assertThat(batch.jobs()).extracting(RemoteMatchQueue.Assignment::slot).containsExactly(1,2);
            assertThat(queue.claim(m.id(),request).jobs()).isEqualTo(batch.jobs());assertThat(queue.claim(m.id(),claim(m,2)).jobs()).isEmpty();
            assertThat(batch.jobs().getFirst().match().seed()).isEqualTo(Long.MAX_VALUE);
            assertThatThrownBy(()->queue.claim(m.id(),new RemoteMatchQueue.Claim(request.requestId(),m.epoch(),1,List.of(),"1","classic-online-v1",1))).isInstanceOf(AuthException.class);
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(m.epoch(),2L,0,2,0,0.0,0.0,0L,0L),"local");assertThat(telemetry.view(m.id()).reservedSlots()).isEqualTo(2);assertThat(telemetry.view(m.id()).effectiveFreeSlots()).isZero();assertThat(telemetry.view(m.id()).status()).isEqualTo("BUSY");
        }
    }
    @Test void concurrentWorkersAndConcurrentClaimsCannotDuplicateJobsOrSlots()throws Exception {
        try(var admin=new Browser();var executor=Executors.newFixedThreadPool(8)) {
            UUID actor=admin.account(true);var machines=List.of(machine(actor,Set.of("CPP"),2,"TRUSTED"),machine(actor,Set.of("CPP"),2,"TRUSTED"));
            var specs=new ArrayList<RemoteMatchQueue.Spec>();for(int i=0;i<8;i++){var spec=spec(actor,"CPP","CPP","TRUSTED");specs.add(spec);queue.enqueue(spec);}
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<RemoteMatchQueue.Batch>>();
            for(int i=0;i<8;i++){var machine=machines.get(i%2);futures.add(executor.submit(()->{gate.await();return queue.claim(machine.id(),claim(machine,2));}));}gate.countDown();var all=new ArrayList<RemoteMatchQueue.Assignment>();for(var future:futures)all.addAll(future.get(20,TimeUnit.SECONDS).jobs());
            assertThat(all).hasSize(4);assertThat(all.stream().map(RemoteMatchQueue.Assignment::jobId).distinct()).hasSize(4);assertThat(all.stream().map(j->j.workerId()+":"+j.slot()).distinct()).hasSize(4);for(var machine:machines)assertThat(telemetry.view(machine.id()).reservedSlots()).isEqualTo(2);
        }
    }
    @Test void compatibilityTrustFreshHeartbeatAndApprovalAreEnforcedWithoutReturningPrivateData()throws Exception {
        try(var admin=new Browser();var guest=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),4,"COMMUNITY");queue.enqueue(spec(actor,"PYTHON","JAVA","COMMUNITY"));queue.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"));var eligible=spec(actor,"PYTHON","PYTHON","COMMUNITY");queue.enqueue(eligible);
            var batch=queue.claim(m.id(),claim(m,4));assertThat(batch.jobs()).hasSize(1);assertThat(batch.jobs().getFirst().match().matchId()).isEqualTo(eligible.matchId());
            String path="/internal/workers/"+m.id()+"/jobs/assigned";assertThat(guest.call(path,"GET",null,null,false).status()).isEqualTo(401);assertThat(admin.call(path,"GET",null,null,false).status()).isEqualTo(401);var response=guest.call(path,"GET",null,m.enrolled().workerCredential(),false);assertThat(response.status()).isEqualTo(200);assertThat(response.body().toString()).doesNotContain("private-fixture","sourceCode","credential","source_path");
            assertThat(guest.call("/internal/workers/"+UUID.randomUUID()+"/jobs/assigned","GET",null,m.enrolled().workerCredential(),false).status()).isEqualTo(403);
            var pending=machine(actor,Set.of("PYTHON"),1,null);assertThatThrownBy(()->queue.claim(pending.id(),claim(pending,1))).isInstanceOf(AuthException.class).hasMessage("WORKER_APPROVAL_REQUIRED");
            db.update("update workers set last_heartbeat_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(31)),m.id());assertThatThrownBy(()->queue.claim(m.id(),claim(m,1))).isInstanceOf(AuthException.class).hasMessage("WORKER_NOT_AVAILABLE");assertThat(telemetry.view(m.id()).reservedSlots()).isEqualTo(1);
        }
    }
    @Test void unstartedDeclineIsOwnedIdempotentAndPreservesOldAttemptWhileNewExecutionGetsNewId()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("JAVA"),1,"TRUSTED");var other=machine(actor,Set.of("JAVA"),1,"TRUSTED");queue.enqueue(spec(actor,"JAVA","JAVA","TRUSTED"));var assignment=queue.claim(m.id(),claim(m,1)).jobs().getFirst();
            var body=new RemoteMatchQueue.Decline(assignment.executionId(),"EXECUTION_NOT_AVAILABLE");assertThatThrownBy(()->queue.decline(other.id(),assignment.jobId(),body)).isInstanceOf(AuthException.class);
            String path="/internal/workers/"+m.id()+"/jobs/"+assignment.jobId()+"/decline";assertThat(client.call(path,"POST",body,m.enrolled().workerCredential(),false).status()).isEqualTo(200);queue.decline(m.id(),assignment.jobId(),body);assertThat(telemetry.view(m.id()).reservedSlots()).isZero();
            var next=queue.claim(other.id(),claim(other,1)).jobs().getFirst();assertThat(next.jobId()).isEqualTo(assignment.jobId());assertThat(next.executionId()).isNotEqualTo(assignment.executionId());assertThat(queue.assigned(m.id()).jobs()).isEmpty();
            assertThat(db.queryForObject("select status from match_execution_attempts where id=?",String.class,assignment.executionId())).isEqualTo("DECLINED");
            db.update("update match_execution_attempts set status='RUNNING' where id=?",next.executionId());assertThatThrownBy(()->queue.decline(other.id(),next.jobId(),new RemoteMatchQueue.Decline(next.executionId(),"SHUTDOWN_BEFORE_START"))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_ALREADY_STARTED");
        }
    }
    @Test void immutableAdmissionAndRestartKeepReservationsAndExpiredLeaseIsNotReassignedPrematurely()throws Exception {
        try(var admin=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var spec=spec(actor,"PYTHON","PYTHON","TRUSTED");UUID job=queue.enqueue(spec);assertThat(queue.enqueue(spec)).isEqualTo(job);
            assertThatThrownBy(()->queue.enqueue(new RemoteMatchQueue.Spec(spec.matchId(),spec.tournamentId(),bot("PYTHON"),spec.botB(),"1","classic-online-v1",2,1000,60000,200,"TRUSTED"))).isInstanceOf(AuthException.class).hasMessage("REMOTE_JOB_IMMUTABLE");
            var old=queue.claim(m.id(),claim(m,1)).jobs().getFirst();long epoch=telemetry.capabilities(m.id(),new WorkerTelemetryService.Capabilities(8,16000L,"Linux",true,Set.of("PYTHON"),1,2,"0.3.0",1)).epoch();assertThat(queue.assigned(m.id()).jobs()).hasSize(1);assertThatThrownBy(()->queue.claim(m.id(),claim(m,1))).isInstanceOf(AuthException.class).hasMessage("STALE_WORKER_SESSION");
            telemetry.heartbeat(m.id(),new WorkerTelemetryService.Heartbeat(epoch,1L,0,1,0,0.0,0.0,0L,0L),"local");db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),old.executionId());
            var fresh=new Machine(m.enrolled(),epoch);assertThat(queue.claim(m.id(),claim(fresh,1)).jobs()).isEmpty();assertThat(telemetry.view(m.id()).reservedSlots()).isEqualTo(1);
        }
    }
    @Test void fifoDoesNotStarveCacheMissAndCompletedMatchesAreNotClaimable()throws Exception {
        try(var admin=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("CPP"),1,"TRUSTED");var old=spec(actor,"CPP","CPP","TRUSTED");var cached=spec(actor,"CPP","CPP","TRUSTED");UUID first=queue.enqueue(old);queue.enqueue(cached);db.update("update match_execution_jobs set created_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(30)),first);
            var req=claim(m,1);var withCache=new RemoteMatchQueue.Claim(req.requestId(),req.epoch(),1,List.of(cached.botA().artifactHash(),cached.botB().artifactHash()),req.gameCoreVersion(),req.ruleSetVersion(),1);assertThat(queue.claim(m.id(),withCache).jobs().getFirst().jobId()).isEqualTo(first);
            var held=queue.assigned(m.id()).jobs().getFirst();queue.decline(m.id(),held.jobId(),new RemoteMatchQueue.Decline(held.executionId(),"EXECUTION_NOT_AVAILABLE"));db.update("update tournament_matches set status='FINISHED' where id=?",old.matchId());assertThat(queue.claim(m.id(),claim(m,1)).jobs().getFirst().match().matchId()).isEqualTo(cached.matchId());
        }
    }
    @Test void databaseConstraintsCapacityAndUntrustedApprovalFenceAllocations()throws Exception {
        try(var admin=new Browser()) {
            UUID actor=admin.account(true);var m=machine(actor,Set.of("PYTHON"),1,"TRUSTED");queue.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"));var held=queue.claim(m.id(),claim(m,1)).jobs().getFirst();
            assertThatThrownBy(()->db.update("insert into match_execution_attempts(id,job_id,match_id,worker_id,heartbeat_epoch,status,active_marker,active_slot,assigned_slot,claimed_at,lease_until) select ?,job_id,match_id,worker_id,heartbeat_epoch,status,active_marker,active_slot,assigned_slot,claimed_at,lease_until from match_execution_attempts where id=?",UUID.randomUUID(),held.executionId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(()->db.update("update match_execution_attempts set active_marker=null where id=?",held.executionId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            db.update("update workers set trust_level='UNTRUSTED' where id=?",m.id());assertThatThrownBy(()->queue.claim(m.id(),claim(m,1))).isInstanceOf(AuthException.class).hasMessage("WORKER_TRUST_REQUIRED");
            var bounded=new RemoteMatchQueue(db,json,Clock.systemUTC(),telemetry,1);assertThatThrownBy(()->bounded.enqueue(spec(actor,"PYTHON","PYTHON","TRUSTED"))).isInstanceOf(AuthException.class).hasMessage("REMOTE_QUEUE_FULL");
        }
    }
}
