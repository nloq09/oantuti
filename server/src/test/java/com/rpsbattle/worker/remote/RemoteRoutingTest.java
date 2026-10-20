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
    @Autowired RemoteRoutingService routing;@Autowired WorkerGroupService groups;@Autowired TournamentRegistry registry;
    @Autowired com.rpsbattle.tournament.TournamentService tournaments;
    @Autowired RemoteLeaseService leases;
    @MockitoSpyBean SubmissionStore submissions;
    @Test void realManagerRoutesLockedBotsWithoutStartingLocalRoomAndPolicyCannotChangeAfterSchedule()throws Exception{
        try(var admin=new Browser()){
            UUID actor=admin.account(true);var base=spec(actor,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(base.tournamentId().toString(),"Routing fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=actor;registry.register(t);submissions.enable(t.id);
            var group=groups.create("ROUTE_"+UUID.randomUUID().toString().substring(0,8),actor,"Official lab");var policy=routing.configure(t.id,actor,new RemoteRoutingService.Configure(0,"REMOTE_REQUIRED",group.id(),"TRUSTED",8,"Use trusted lab"));assertThat(policy.revision()).isEqualTo(1);
            assertThatThrownBy(()->routing.configure(t.id,actor,new RemoteRoutingService.Configure(0,"LOCAL",null,"TRUSTED",4,"Stale"))).isInstanceOf(AuthException.class).hasMessage("STALE_REMOTE_POLICY");
            var row=db.queryForMap("select * from tournament_matches where id=?",base.matchId());
            for(String column:List.of("participant_a_id","participant_b_id")){
                var data=db.queryForMap("select * from tournament_participants where id=?",row.get(column));String hash=WorkerIdentityService.hash(column);var seat=new TournamentParticipant(data.get("id").toString(),data.get("bot_id").toString(),"never-send-token","Bot",column.equals("participant_a_id")?7L:9L,actor).withHash(hash);t.participants.add(seat);
                doReturn(new WorkspaceStore.Work(UUID.randomUUID(),UUID.randomUUID(),"PYTHON","DO_NOT_SEND_SOURCE","a".repeat(64),UUID.randomUUID(),hash)).when(submissions).execution(t.id,seat.participantId(),seat.botId());
            }
            var m=new TournamentMatch(base.matchId().toString(),t.id,t.participants.get(0),t.participants.get(1));t.matches.add(m);t.status=TournamentStatus.RUNNING;db.update("update tournaments set status='RUNNING' where id=?",base.tournamentId());
            var local=mock(MatchWorker.class);var manager=new WorkerManager(new ExecutionQueue(256),local,tournaments,4,Runnable::run);manager.routing(routing);manager.offer(new MatchExecutionRequest(t,m));manager.dispatch();verifyNoInteractions(local);assertThat(manager.active()).isZero();assertThat(m.status).isEqualTo(TournamentMatch.Status.PENDING);
            var job=db.queryForMap("select * from match_execution_jobs where match_id=?",base.matchId());assertThat(job.get("spec_json").toString()).contains("\"seed\":14").doesNotContain("DO_NOT_SEND_SOURCE","never-send-token");assertThat(db.queryForObject("select group_id from remote_job_routes where job_id=?",UUID.class,job.get("id"))).isEqualTo(group.id());
            assertThatThrownBy(()->routing.configure(t.id,actor,new RemoteRoutingService.Configure(1,"LOCAL",null,"TRUSTED",4,"Too late"))).isInstanceOf(AuthException.class).hasMessage("REMOTE_POLICY_LOCKED");
            var community=machine(actor,Set.of("PYTHON"),1,"COMMUNITY");groups.change(group.id(),actor,new WorkerGroupService.Change(group.revision(),null,community.id(),true,"Public machine"));assertThat(queue.claim(community.id(),claim(community,1)).jobs()).isEmpty();
            var trusted=machine(actor,Set.of("PYTHON"),1,"TRUSTED");var latest=groups.group(group.id());groups.change(group.id(),actor,new WorkerGroupService.Change(latest.revision(),null,trusted.id(),true,"Official machine"));var assignment=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();
            for(int i=0;i<3;i++){leases.recover(trusted.id(),new RemoteLeaseService.Recovery(assignment.executionId(),"ABANDON"));if(i<2)assignment=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();}
            assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,assignment.jobId())).isEqualTo("CANCELLED");
            m.attempt++;routing.route(t,m);var retried=queue.claim(trusted.id(),claim(trusted,1)).jobs().getFirst();assertThat(retried.executionId()).isNotEqualTo(assignment.executionId());leases.recover(trusted.id(),new RemoteLeaseService.Recovery(retried.executionId(),"ABANDON"));assertThat(db.queryForObject("select status from match_execution_jobs where id=?",String.class,retried.jobId())).isEqualTo("QUEUED");
        }
    }
    @Test void ordinaryAccountsCannotConfigureRoutingAndUnfrozenOrHumanTournamentsAreRejected()throws Exception{
        try(var admin=new Browser();var user=new Browser()){
            UUID actor=admin.account(true);user.account(false);var data=spec(actor,"PYTHON","PYTHON","TRUSTED");var t=new Tournament(data.tournamentId().toString(),"Local default",TournamentFormat.ROUND_ROBIN,Instant.now());registry.register(t);
            assertThat(routing.policy(t.id).mode()).isEqualTo("LOCAL");assertThat(routing.remote(t.id)).isFalse();
            var command=new RemoteRoutingService.Configure(0,"REMOTE_REQUIRED",UUID.randomUUID(),"TRUSTED",4,"Attempt");assertThat(user.call("/api/admin/tournaments/"+t.id+"/worker-policy","POST",command,null,true).status()).isEqualTo(403);
            assertThatThrownBy(()->routing.configure(t.id,actor,command)).isInstanceOf(AuthException.class).hasMessage("REMOTE_REQUIRES_LOCKED_CODING_BOTS");
        }
    }
}
