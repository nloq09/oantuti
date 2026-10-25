package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.coding.SubmissionStore;
import java.util.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class TournamentWorkerTest extends RemoteJobClaimTest {
    @Autowired TournamentRegistry registry;@Autowired SubmissionStore submissions;
    @Autowired RemoteArtifactService artifacts;@Autowired RemoteResultService results;@Autowired RemoteLeaseService leases;
    @Autowired WorkerOperationsService operations;
    record Cup(Tournament tournament,RemoteMatchQueue.Spec spec){String id(){return tournament.id;}String base(){return "/api/tournaments/"+id()+"/workers";}}
    Cup cup(UUID owner){var spec=spec(owner,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(spec.tournamentId().toString(),"Owner workers",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=owner;t.deferNotifications=true;registry.register(t);submissions.enable(t.id);return new Cup(t,spec);}
    Reply token(Browser owner,Cup cup)throws Exception{return owner.call(cup.base()+"/registration-tokens","POST",Map.of("trusted",true),null,true);}
    Machine enroll(Browser owner,Cup cup)throws Exception{
        var issued=token(owner,cup);assertThat(issued.status()).isEqualTo(201);var body=new HashMap<String,Object>();body.put("registrationToken",issued.body().path("registrationToken").asText());body.put("name","Scoped machine");body.put("workerVersion","0.11.0");body.put("protocolVersion",1);body.put("tournamentId",UUID.randomUUID());body.put("scope","GLOBAL");
        var response=owner.call("/internal/workers/register","POST",body,null,false);assertThat(response.status()).isEqualTo(201);var enrolled=json.convertValue(response.body(),WorkerIdentityService.Enrolled.class);
        UUID id=enrolled.worker().workerId();long epoch=telemetry.capabilities(id,new WorkerTelemetryService.Capabilities(4,8192L,"Linux",true,Set.of("PYTHON"),2,4,"0.11.0",1)).epoch();telemetry.heartbeat(id,new WorkerTelemetryService.Heartbeat(epoch,1L,0,2,0,10.0,20.0,10000L,0L),"local");return new Machine(enrolled,epoch);
    }
    @Test void ownerCanEnrollOnlyForOwnTournamentWithCsrfAndExplicitTrustWithoutGainingAdminPrivileges()throws Exception{
        try(var owner=new Browser();var other=new Browser();var guest=new Browser()){
            UUID actor=owner.account(false),stranger=other.account(false);var a=cup(actor);var b=cup(stranger);
            assertThat(guest.call(a.base(),"GET",null,null,false).status()).isEqualTo(401);
            assertThat(other.call(a.base(),"GET",null,null,false).status()).isEqualTo(403);
            assertThat(other.call(a.base()+"/registration-tokens","POST",Map.of("trusted",true),null,true).status()).isEqualTo(403);
            assertThat(owner.call(a.base()+"/registration-tokens","POST",Map.of("trusted",true),null,false).status()).isEqualTo(403);
            assertThat(owner.call(a.base()+"/registration-tokens","POST",Map.of("trusted",false),null,true).status()).isEqualTo(400);
            assertThat(owner.call("/api/admin/workers/registration-tokens","POST",Map.of("uses",1),null,true).status()).isEqualTo(403);
            var machine=enroll(owner,a);assertThat(machine.enrolled().worker().approvalStatus()).isEqualTo("APPROVED");assertThat(machine.enrolled().worker().trustLevel()).isEqualTo("TRUSTED");
            assertThat(db.queryForObject("select tournament_id from worker_tournament_scopes where worker_id=?",UUID.class,machine.id())).isEqualTo(a.spec().tournamentId());
            var view=owner.call(a.base(),"GET",null,null,false);assertThat(view.status()).isEqualTo(200);assertThat(view.body().path("workers")).hasSize(1);assertThat(view.body().toString()).doesNotContain(machine.enrolled().workerCredential(),"token_hash","credential_hash","registrationToken");assertThat(other.call(b.base(),"GET",null,null,false).body().path("workers")).isEmpty();
            var jar=owner.http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+a.base()+"/download")).GET().build(),HttpResponse.BodyHandlers.discarding());assertThat(jar.statusCode()).isEqualTo(200);assertThat(jar.headers().firstValue("Content-Disposition").orElse("")).contains("rps-worker.jar");assertThat(other.call(a.base()+"/download","GET",null,null,false).status()).isEqualTo(403);
            assertThat(identities.tokens().stream().filter(t->a.spec().tournamentId().equals(t.tournamentId())).findFirst()).isPresent();
        }
    }
    @Test void scopedMachinesCannotClaimOtherTournamentsButPlatformMachinesKeepTheirSharedPool()throws Exception{
        try(var owner=new Browser();var other=new Browser()){
            UUID actor=owner.account(false),second=other.account(false);db.update("update workers set last_heartbeat_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(60)));var a=cup(actor);var b=cup(second);var privateA=enroll(owner,a);var privateB=enroll(other,b);
            assertThat(queue.hasEligibleWorker(a.spec().tournamentId(),Set.of("PYTHON"),"TRUSTED")).isTrue();
            UUID firstB=queue.enqueue(b.spec());UUID firstA=queue.enqueue(a.spec());var claim=owner.call("/internal/workers/"+privateA.id()+"/jobs/claim","POST",claim(privateA,2),privateA.enrolled().workerCredential(),false);assertThat(claim.status()).isEqualTo(200);assertThat(claim.body().path("jobs")).hasSize(1);assertThat(claim.body().path("jobs").get(0).path("jobId").asText()).isEqualTo(firstA.toString());
            UUID executionA=UUID.fromString(claim.body().path("jobs").get(0).path("executionId").asText());assertThat(queue.start(privateA.id(),firstA,new RemoteMatchQueue.Start(executionA,privateA.epoch())).status()).isEqualTo("RUNNING");assertThat(leases.recover(privateA.id(),new RemoteLeaseService.Recovery(executionA,"RESULT_READY")).status()).isEqualTo("UPLOAD_ALLOWED");
            var jobB=queue.claim(privateB.id(),claim(privateB,2)).jobs().getFirst();assertThat(jobB.jobId()).isEqualTo(firstB);
            assertThatThrownBy(()->queue.start(privateA.id(),firstB,new RemoteMatchQueue.Start(jobB.executionId(),privateA.epoch()))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");
            assertThatThrownBy(()->artifacts.download(privateA.id(),jobB.executionId(),b.spec().botA().artifactId())).isInstanceOf(AuthException.class).hasMessage("ARTIFACT_NOT_ASSIGNED");
            assertThatThrownBy(()->leases.recover(privateA.id(),new RemoteLeaseService.Recovery(jobB.executionId(),"RESULT_READY"))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");
            byte[] bytes="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var bad=new RemoteResultService.Result(jobB.executionId(),b.spec().matchId(),null,null,"ENGINE_RESULT",1,1L,b.spec().botA().artifactHash(),b.spec().botB().artifactHash(),b.spec().seed(),RemoteResultService.hash(bytes));assertThatThrownBy(()->results.accept(privateA.id(),firstB,new RemoteResultService.Upload(bad,Base64.getEncoder().encodeToString(bytes)))).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");
            UUID extra=UUID.randomUUID();var seats=db.queryForMap("select participant_a_id,participant_b_id from tournament_matches where id=?",a.spec().matchId());db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",extra,a.spec().tournamentId(),seats.get("participant_a_id"),seats.get("participant_b_id"));queue.enqueue(new RemoteMatchQueue.Spec(extra,a.spec().tournamentId(),a.spec().botA(),a.spec().botB(),"1","classic-online-v1",1,1000,60000,200,"TRUSTED"));assertThat(queue.claim(privateB.id(),claim(privateB,2)).jobs()).isEmpty();
            var platform=machine(actor,Set.of("PYTHON"),2,"TRUSTED");assertThat(queue.claim(platform.id(),claim(platform,2)).jobs()).extracting(j->j.match().tournamentId()).containsExactly(a.spec().tournamentId());
        }
    }
    @Test void ownerCannotControlAnotherTournamentOrPlatformWorkerAndRevocationDoesNotPromoteMachineToGlobal()throws Exception{
        try(var owner=new Browser();var other=new Browser()){
            UUID actor=owner.account(false),second=other.account(false);var a=cup(actor);var b=cup(second);var privateA=enroll(owner,a);var privateB=enroll(other,b);var platform=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var command=new WorkerControlService.Command(0,"REVOKE","Remove own machine");
            for(UUID id:List.of(privateB.id(),platform.id()))assertThat(owner.call(a.base()+"/"+id+"/control","POST",command,null,true).status()).isEqualTo(404);
            assertThat(other.call(a.base()+"/"+privateA.id()+"/control","POST",command,null,true).status()).isEqualTo(403);assertThat(owner.call(a.base()+"/"+privateA.id()+"/control","POST",command,null,false).status()).isEqualTo(403);
            assertThat(owner.call(a.base()+"/"+privateA.id()+"/control","POST",command,null,true).status()).isEqualTo(200);assertThatThrownBy(()->identities.authenticate(privateA.enrolled().workerCredential())).hasMessage("WORKER_CREDENTIAL_INVALID");assertThat(WorkerScope.allows(db,privateA.id(),b.spec().tournamentId())).isFalse();
        }
    }
    @Test void unusedTokensAreScopedRevocableLimitedAndRejectedAfterTournamentEnds()throws Exception{
        try(var owner=new Browser();var other=new Browser()){
            UUID actor=owner.account(false),second=other.account(false);var a=cup(actor);var b=cup(second);var tokens=new ArrayList<Reply>();for(int i=0;i<5;i++)tokens.add(token(owner,a));assertThat(token(owner,a).status()).isEqualTo(409);
            UUID token=UUID.fromString(tokens.getFirst().body().path("id").asText());assertThat(other.call(b.base()+"/registration-tokens/"+token+"/revoke","POST",Map.of(),null,true).status()).isEqualTo(404);assertThat(owner.call(a.base()+"/registration-tokens/"+token+"/revoke","POST",Map.of(),null,true).status()).isEqualTo(200);
            String raw=tokens.getFirst().body().path("registrationToken").asText();assertThatThrownBy(()->identities.register(registration(raw))).hasMessage("WORKER_REGISTRATION_TOKEN_INVALID");assertThat(token(owner,a).status()).isEqualTo(201);
            a.tournament().status=TournamentStatus.CANCELLED;db.update("update tournaments set status='CANCELLED' where id=?",a.spec().tournamentId());assertThat(token(owner,a).status()).isEqualTo(409);String pending=tokens.get(1).body().path("registrationToken").asText();assertThatThrownBy(()->identities.register(registration(pending))).hasMessage("WORKER_REGISTRATION_TOKEN_INVALID");
        }
    }
    @Test void automaticCapacityNeverCountsPrivateMachineOfAnotherTournament()throws Exception{
        try(var owner=new Browser()){
            UUID actor=owner.account(false);db.update("update workers set last_heartbeat_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(60)));var a=cup(actor);var b=cup(actor);enroll(owner,a);
            assertThat(queue.hasEligibleWorker(a.spec().tournamentId(),Set.of("PYTHON"),"TRUSTED")).isTrue();assertThat(queue.hasEligibleWorker(b.spec().tournamentId(),Set.of("PYTHON"),"TRUSTED")).isFalse();
            @SuppressWarnings("unchecked") var capacity=(Map<String,Object>)operations.capacity(b.spec().tournamentId(),"TRUSTED",1);assertThat(capacity).containsEntry("freeSlots",0);
        }
    }
}
