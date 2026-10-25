package com.rpsbattle.worker.remote;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@org.springframework.boot.test.context.SpringBootTest(webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class RemoteRoutingTest extends RemoteJobClaimTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.rpsbattle.tournament.execution.TournamentExecutionService scheduledExecution;
    @Autowired RemoteRoutingService routing;@Autowired TournamentRegistry registry;
    @Autowired com.rpsbattle.tournament.TournamentService tournaments;
    @Autowired RemoteLeaseService leases;
    @MockitoSpyBean SubmissionStore submissions;
    @Test void realManagerRoutesLockedBotsWithoutStartingLocalRoomAndPolicyCannotChangeAfterSchedule()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var base=spec(actor,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(base.tournamentId().toString(),"Routing fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=actor;t.deferNotifications=true;registry.register(t);submissions.enable(t.id);
            var policy=routing.configure(t.id,actor,new RemoteRoutingService.Configure(0,"REMOTE_REQUIRED","TRUSTED",8,"Use trusted lab"));assertThat(policy.revision()).isEqualTo(1);
            assertThatThrownBy(()->routing.configure(t.id,actor,new RemoteRoutingService.Configure(0,"LOCAL","TRUSTED",4,"Stale"))).isInstanceOf(AuthException.class).hasMessage("STALE_REMOTE_POLICY");
            var row=db.queryForMap("select * from tournament_matches where id=?",base.matchId());
            for(String column:List.of("participant_a_id","participant_b_id")){
                var data=db.queryForMap("select * from tournament_participants where id=?",row.get(column));String hash=WorkerIdentityService.hash(column);var seat=new TournamentParticipant(data.get("id").toString(),data.get("bot_id").toString(),"never-send-token","Bot",column.equals("participant_a_id")?7L:9L,actor).withHash(hash);t.participants.add(seat);
                doReturn(new WorkspaceStore.Work(UUID.randomUUID(),UUID.randomUUID(),"PYTHON","DO_NOT_SEND_SOURCE","a".repeat(64),UUID.randomUUID(),hash)).when(submissions).execution(t.id,seat.participantId(),seat.botId());
            }
            var m=new TournamentMatch(base.matchId().toString(),t.id,t.participants.get(0),t.participants.get(1));t.matches.add(m);t.status=TournamentStatus.RUNNING;db.update("update tournaments set status='RUNNING' where id=?",base.tournamentId());
            var local=mock(MatchWorker.class);var manager=new WorkerManager(new ExecutionQueue(256),local,tournaments,4,Runnable::run);manager.routing(routing);manager.offer(new MatchExecutionRequest(t,m));manager.dispatch();verifyNoInteractions(local);assertThat(manager.active()).isZero();assertThat(m.status).isEqualTo(TournamentMatch.Status.PENDING);
            var job=db.queryForMap("select * from match_execution_jobs where match_id=?",base.matchId());assertThat(job.get("spec_json").toString()).contains("\"seed\":14").doesNotContain("DO_NOT_SEND_SOURCE","never-send-token");assertThat(db.queryForObject("select count(*) from worker_pool_routes where job_id=?",Integer.class,job.get("id"))).isEqualTo(1);
            assertThatThrownBy(()->routing.configure(t.id,actor,new RemoteRoutingService.Configure(1,"LOCAL","TRUSTED",4,"Too late"))).isInstanceOf(AuthException.class).hasMessage("REMOTE_POLICY_LOCKED");
            var community=machine(actor,Set.of("PYTHON"),1,"COMMUNITY");assertThat(queue.claim(community.id(),claim(community,1)).jobs()).isEmpty();
            var trusted=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var assignment=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();
            for(int i=0;i<3;i++){leases.recover(trusted.id(),new RemoteLeaseService.Recovery(assignment.executionId(),"ABANDON"));if(i<2)assignment=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();}
            assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,assignment.jobId())).isEqualTo("CANCELLED");
            m.attempt++;routing.route(t,m);var retried=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();assertThat(retried.executionId()).isNotEqualTo(assignment.executionId());leases.recover(trusted.id(),new RemoteLeaseService.Recovery(retried.executionId(),"ABANDON"));assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,retried.jobId())).isEqualTo("QUEUED");
        }
    }
    @Test void ordinaryAccountsCannotConfigureRoutingAndUnfrozenOrHumanTournamentsAreRejected()throws Exception{
        try(var admin=new Browser();var user=new Browser()){
            UUID actor=admin.account(true);user.account(false);var data=spec(actor,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(data.tournamentId().toString(),"Local default",TournamentFormat.ROUND_ROBIN,Instant.now());registry.register(t);
            assertThat(routing.policy(t.id).mode()).isEqualTo("AUTO");assertThat(routing.remote(t.id)).isFalse();
            var command=new RemoteRoutingService.Configure(0,"REMOTE_REQUIRED","TRUSTED",4,"Attempt");assertThat(user.call("/api/admin/tournaments/"+t.id+"/worker-policy","POST",command,null,true).status()).isEqualTo(403);
            assertThatThrownBy(()->routing.configure(t.id,actor,command)).isInstanceOf(AuthException.class).hasMessage("REMOTE_REQUIRES_LOCKED_CODING_BOTS");
        }
    }
    record Fixture(Tournament tournament,TournamentMatch match){}
    Fixture fixture(UUID actor){return fixture(actor,TournamentFormat.ROUND_ROBIN);}
    Fixture fixture(UUID actor,TournamentFormat format){
        var base=spec(actor,"PYTHON","PYTHON","TRUSTED");db.update("update tournaments set format=? where id=?",format.name(),base.tournamentId());var t=new Tournament(base.tournamentId().toString(),"Automatic pool",format,Instant.now());t.ownerUserId=actor;t.deferNotifications=true;submissions.enable(t.id);
        var row=db.queryForMap("select * from tournament_matches where id=?",base.matchId());
        for(String column:List.of("participant_a_id","participant_b_id")){
            var data=db.queryForMap("select * from tournament_participants where id=?",row.get(column));String hash=WorkerIdentityService.hash(column);var seat=new TournamentParticipant(data.get("id").toString(),data.get("bot_id").toString(),"private-token","Bot",column.equals("participant_a_id")?7L:9L,actor).withHash(hash);t.participants.add(seat);
            doReturn(new WorkspaceStore.Work(UUID.randomUUID(),UUID.randomUUID(),"PYTHON","private-source","a".repeat(64),UUID.randomUUID(),hash)).when(submissions).execution(t.id,seat.participantId(),seat.botId());
        }
        // The test owns these SQL fixtures; publish only after assembly and suppress background snapshots.
        var m=new TournamentMatch(base.matchId().toString(),t.id,t.participants.get(0),t.participants.get(1));t.matches.add(m);t.status=TournamentStatus.RUNNING;db.update("update tournaments set status='RUNNING' where id=?",base.tournamentId());registry.register(t);return new Fixture(t,m);
    }
    @Test void automaticPoolRoutesMultipleTournamentsWithoutAnyPolicyOrMembershipAndNeverFallsBackAfterAdmission()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var a=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var b=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var one=fixture(actor);var two=fixture(actor);
            assertThat(routing.policy(one.tournament().id).mode()).isEqualTo("AUTO");
            var local=mock(MatchWorker.class);var manager=new WorkerManager(new ExecutionQueue(256),local,tournaments,4,Runnable::run);manager.routing(routing);
            for(var f:List.of(one,two)){manager.offer(new MatchExecutionRequest(f.tournament(),f.match()));manager.dispatch();}verifyNoInteractions(local);
            var first=queue.claim(a.id(),claim(a,1)).jobs().getFirst();var second=queue.claim(b.id(),claim(b,1)).jobs().getFirst();assertThat(first.match().tournamentId()).isNotEqualTo(second.match().tournamentId());assertThat(first.jobId()).isNotEqualTo(second.jobId());
            // Even with every worker offline, re-admission cannot switch an existing job to local.
            db.update("update workers set last_heartbeat_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
            assertThat(routing.route(one.tournament(),one.match())).isTrue();assertThat(db.queryForObject("select count(*) from match_execution_jobs",Integer.class)).isEqualTo(2);
        }
    }
    @Test void automaticPoolProtectsPrivateBotsAndUsesLocalOnlyBeforeAdmissionWhenNoCompatibleMachineExists()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);db.update("update workers set last_heartbeat_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(60)));var f=fixture(actor);
            machine(actor,Set.of("PYTHON"),1,"COMMUNITY");machine(actor,Set.of("JAVA"),1,"TRUSTED");machine(actor,Set.of("PYTHON"),1,null);
            assertThat(routing.route(f.tournament(),f.match())).isFalse();assertThat(db.queryForObject("select count(*) from match_execution_jobs",Integer.class)).isZero();
            var trusted=machine(actor,Set.of("PYTHON"),1,"TRUSTED");assertThat(routing.route(f.tournament(),f.match())).isTrue();var running=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();
            // A busy but online machine keeps new work in the remote pool instead of overloading Central.
            var next=fixture(actor);assertThat(routing.route(next.tournament(),next.match())).isTrue();assertThat(queue.claim(trusted.id(),claim(trusted,1)).jobs()).isEmpty();
            queue.decline(trusted.id(),running.jobId(),new RemoteMatchQueue.Decline(running.executionId(),"EXECUTION_NOT_AVAILABLE"));assertThat(queue.claim(trusted.id(),claim(trusted,1)).jobs()).hasSize(1);
        }
    }


    @Test void assignedRunningAndUnprojectedRemoteMatchesBlockParticipantDiscipline()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var f=fixture(actor);var t=f.tournament();var m=f.match();
            assertThat(routing.route(t,m)).isTrue();var assigned=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();
            String base="/api/tournaments/"+t.id;
            assertThat(admin.call(base+"/control/pause","POST",Map.of("revision",t.revision),null,true).status()).isEqualTo(200);
            long revision=t.revision;
            for(String state:List.of("CLAIMED","RUNNING","DONE")){
                if(state.equals("RUNNING"))queue.gameplay(machine.id(),assigned.jobId(),new RemoteMatchQueue.Start(assigned.executionId(),machine.epoch()));
                if(state.equals("DONE"))db.update("update match_execution_jobs set status='DONE' where id=?",assigned.jobId());
                for(String action:List.of("DISQUALIFIED","WITHDRAWN")){
                    var result=admin.call(base+"/participants/"+m.participantA.participantId()+"/status","POST",Map.of("revision",t.revision,"status",action,"reason","Do not rewrite an active remote match"),null,true);
                    assertThat(result.status()).as(result.body().toString()).isEqualTo(400);assertThat(result.body().path("error").asText()).isEqualTo("PARTICIPANT_BUSY");
                    assertThat(t.revision).isEqualTo(revision);assertThat(m.status).isEqualTo(TournamentMatch.Status.PENDING);assertThat(t.operations.active(m.participantA.participantId())).isTrue();
                }
            }
        }
    }
    @Test void queuedUnclaimedRemoteMatchCanStillBeForfeitedAndCannotBeClaimedAfterward()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var f=fixture(actor);var t=f.tournament();var m=f.match();
            assertThat(routing.route(t,m)).isTrue();String base="/api/tournaments/"+t.id;
            assertThat(admin.call(base+"/control/pause","POST",Map.of("revision",t.revision),null,true).status()).isEqualTo(200);
            var result=admin.call(base+"/participants/"+m.participantA.participantId()+"/status","POST",Map.of("revision",t.revision,"status","DISQUALIFIED","reason","Unplayed entrant is ineligible"),null,true);
            assertThat(result.status()).as(result.body().toString()).isEqualTo(200);assertThat(m.status).isEqualTo(TournamentMatch.Status.FINISHED);assertThat(m.decisionReason).isEqualTo("FORFEIT");
            assertThat(queue.claim(machine.id(),claim(machine,1)).jobs()).isEmpty();
        }
    }

    @Test void knockoutOverrideCannotRemoveAlreadyRoutedDownstreamMatches()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");
            for(boolean claim:List.of(false,true)){
                var f=fixture(actor,TournamentFormat.SINGLE_ELIMINATION);var t=f.tournament();var downstream=f.match();downstream.round=2;
                var prior=new TournamentMatch(UUID.randomUUID().toString(),t.id,downstream.participantA,downstream.participantB);prior.round=1;prior.status=TournamentMatch.Status.FINISHED;prior.result="WIN";prior.winnerParticipantId=prior.participantA.participantId();t.matches.add(prior);t.operations=t.operations.review(prior.id,"Review prior result");
                assertThat(routing.route(t,downstream)).isTrue();if(claim)assertThat(queue.claim(machine.id(),claim(machine,1)).jobs()).hasSize(1);
                String base="/api/tournaments/"+t.id;assertThat(admin.call(base+"/control/pause","POST",Map.of("revision",t.revision),null,true).status()).isEqualTo(200);long revision=t.revision;
                var result=admin.call(base+"/matches/"+prior.id+"/override","POST",Map.of("revision",revision,"winnerParticipantId",prior.participantB.participantId(),"reason","Must not replace downstream remote match"),null,true);
                assertThat(result.status()).as(result.body().toString()).isEqualTo(400);assertThat(result.body().path("error").asText()).isEqualTo("DOWNSTREAM_MATCH_STARTED");
                assertThat(t.revision).isEqualTo(revision);assertThat(t.matches).contains(downstream);assertThat(prior.winnerParticipantId).isEqualTo(prior.participantA.participantId());
            }
        }
    }
}
