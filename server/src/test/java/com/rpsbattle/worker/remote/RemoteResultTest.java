package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.*;
import com.rpsbattle.match.*;
import com.rpsbattle.replay.*;
import com.rpsbattle.rules.online.OnlineRuleSet;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rps.worker.result-project-millis=3600000")
class RemoteResultTest extends RemoteJobClaimTest {
    static final Path REPLAYS=temporary();static Path temporary(){try{return Files.createTempDirectory("remote-results-");}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void files(DynamicPropertyRegistry r){r.add("rps.replays.directory",()->REPLAYS.toString());}
    @Autowired RemoteResultService results;@Autowired RemoteResultProjector projector;@Autowired TournamentRegistry registry;@Autowired TournamentPersistenceCoordinator persistence;
    @Autowired com.rpsbattle.tournament.TournamentService tournaments;
    @MockitoSpyBean ReplayArchiveStore archives;
    record Fixture(Machine machine,RemoteMatchQueue.Assignment job,Tournament tournament,RemoteResultService.Upload upload){}
    Fixture fixture(UUID actor)throws Exception {
        var base=spec(actor,"PYTHON","PYTHON","TRUSTED");var spec=new RemoteMatchQueue.Spec(base.matchId(),base.tournamentId(),base.botA(),base.botB(),"1","classic-online-v1",7,1000,60000,8,"TRUSTED");
        var t=new Tournament(spec.tournamentId().toString(),"Result fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.ownerUserId=actor;t.status=TournamentStatus.PAUSED;
        var row=db.queryForList("select * from tournament_matches where id=?",spec.matchId()).getFirst();for(String key:List.of("participant_a_id","participant_b_id")){var p=db.queryForList("select * from tournament_participants where id=?",row.get(key)).getFirst();t.participants.add(new TournamentParticipant(p.get("id").toString(),p.get("bot_id").toString(),"","Fixture",1,actor,null,null,null));}
        var entry=new TournamentMatch(spec.matchId().toString(),t.id,t.participants.get(0),t.participants.get(1));t.matches.add(entry);registry.register(t);persistence.persistNow(t);
        var machine=machine(actor,Set.of("PYTHON"),1,"TRUSTED");queue.enqueue(spec);var job=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();queue.start(machine.id(),job.jobId(),new RemoteMatchQueue.Start(job.executionId(),machine.epoch()));
        var room=new OnlineMatch(spec.matchId().toString(),MatchMode.HUMAN_VS_HUMAN,7,Clock.systemUTC(),Duration.ofSeconds(1));com.rpsbattle.agent.PlayerAgent agent=(view,actions)->actions.getFirst();room.attachBot(OnlineMatch.P1,agent,"A");room.join();room.attachBot(OnlineMatch.P2,agent,"B");room.prepareBots();
        var match=new Match(room.state().withPhase(com.rpsbattle.game.state.GamePhase.READY),new OnlineRuleSet(OnlineMatch.P1,OnlineMatch.P2,8),agent,agent,7L);match.runToCompletion();Path file=Files.createTempFile(REPLAYS,"fixture-",".json");new ReplayService().write(file,match);byte[] bytes=Files.readAllBytes(file);Files.delete(file);String winner=match.state().result().orElseThrow().winner().map(p->p.value()).orElse(null);
        var result=new RemoteResultService.Result(job.executionId(),spec.matchId(),winner,winner==null?null:winner.equals("P1")?"P2":"P1","ENGINE_RESULT",match.state().turnNumber(),10L,spec.botA().artifactHash(),spec.botB().artifactHash(),7L,RemoteResultService.hash(bytes));
        return new Fixture(machine,job,t,new RemoteResultService.Upload(result,Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void measuredMoveTimingSurvivesUploadRetryProjectionAndRanking()throws Exception {
        try(var admin=new Browser()){
            var f=fixture(admin.account(true));var r=f.upload.result();
            var timing=new RemoteResultService.MoveTiming(4_000_000L,6_000_000L,4,4);
            var measured=new RemoteResultService.Result(r.executionId(),r.matchId(),r.winner(),r.loser(),r.reason(),r.turnCount(),r.durationMs(),r.artifactHashA(),r.artifactHashB(),r.seed(),r.replayChecksum(),timing);
            var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(r);payload.set("moveTiming",json.valueToTree(timing));
            assertThat(json.writeValueAsString(measured)).isEqualTo(json.writeValueAsString(payload));
            var upload=new RemoteResultService.Upload(measured,f.upload.replayBase64());
            var receipt=results.accept(f.machine.id(),f.job.jobId(),upload);
            assertThat(receipt.resultHash()).isEqualTo(WorkerIdentityService.hash(json.writeValueAsString(payload)));
            assertThat(results.accept(f.machine.id(),f.job.jobId(),upload)).isEqualTo(receipt);
            projector.project(f.job.executionId());projector.project(f.job.executionId());
            var m=f.tournament.matches.getFirst();assertThat(m.moveCountA).isEqualTo(4);assertThat(m.moveNanosB).isEqualTo(6_000_000L);
            var ranked=tournaments.ranking(f.tournament);assertThat(ranked.stream().filter(x->x.participantId().equals(m.participantA.participantId())).findFirst().orElseThrow().averageMoveMillis()).isEqualTo(1.0);
            assertThat(ranked.stream().filter(x->x.participantId().equals(m.participantB.participantId())).findFirst().orElseThrow().averageMoveMillis()).isEqualTo(1.5);
            var stored=db.queryForObject("select structure_json from tournaments where id=?",String.class,UUID.fromString(f.tournament.id));assertThat(stored).contains("6000000");
        }
    }
    @Test void legacyResultHashRemainsStableAndInvalidTimingCannotReleaseWorkerSlot()throws Exception {
        try(var admin=new Browser()){
            var f=fixture(admin.account(true));var r=f.upload.result();
            assertThat(json.writeValueAsString(r)).doesNotContain("moveTiming");
            for(var bad:List.of(new RemoteResultService.MoveTiming(-1L,0L,1,1),new RemoteResultService.MoveTiming(100_000_000L,0L,1,1),new RemoteResultService.MoveTiming(1L,1L,0,1),new RemoteResultService.MoveTiming(null,1L,1,1),new RemoteResultService.MoveTiming(1L,1L,10000,1))){
                var measured=new RemoteResultService.Result(r.executionId(),r.matchId(),r.winner(),r.loser(),r.reason(),r.turnCount(),r.durationMs(),r.artifactHashA(),r.artifactHashB(),r.seed(),r.replayChecksum(),bad);
                assertThatThrownBy(()->results.accept(f.machine.id(),f.job.jobId(),new RemoteResultService.Upload(measured,f.upload.replayBase64()))).hasMessage("INVALID_REMOTE_TIMING");
            }
            assertThat(telemetry.view(f.machine.id()).reservedSlots()).isEqualTo(1);
            var receipt=results.accept(f.machine.id(),f.job.jobId(),f.upload);assertThat(receipt.resultHash()).isEqualTo(WorkerIdentityService.hash(json.writeValueAsString(r)));
            projector.project(f.job.executionId());assertThat(tournaments.ranking(f.tournament)).allMatch(row->row.averageMoveMillis()==null);
        }
    }
    @Test void concurrentRetriesCommitOnceReleaseSlotAndProjectHistoryReplayAndAuditExactlyOnce()throws Exception {
        try(var admin=new Browser();var client=new Browser();var pool=Executors.newFixedThreadPool(4)){
            var f=fixture(admin.account(true));var tasks=new ArrayList<Future<RemoteResultService.Receipt>>();for(int i=0;i<4;i++)tasks.add(pool.submit(()->results.accept(f.machine.id(),f.job.jobId(),f.upload)));for(var task:tasks)assertThat(task.get(20,TimeUnit.SECONDS).status()).isEqualTo("ACCEPTED");
            assertThat(results.pendingOwnership()).contains(f.job.match().matchId().toString());
            assertThat(telemetry.view(f.machine.id()).reservedSlots()).isZero();assertThat(db.queryForObject("select count(*) from worker_result_receipts where execution_id=?",Integer.class,f.job.executionId())).isEqualTo(1);
            projector.project(f.job.executionId());projector.project(f.job.executionId());var m=f.tournament.matches.getFirst();assertThat(m.status).isEqualTo(TournamentMatch.Status.FINISHED);assertThat(m.historyMatchId).isEqualTo(m.id);assertThat(archives.load(m.id).log().matchId()).isEqualTo(m.id);assertThat(f.tournament.operations.audit().stream().filter(e->e.type().equals("MATCH_FINISHED"))).hasSize(1);
            assertThat(results.pendingOwnership()).doesNotContain(m.id);
            assertThat(tournaments.ranking(f.tournament)).allMatch(row->row.played()==1);
            var knockout=new Tournament(UUID.randomUUID().toString(),"Projection bracket",TournamentFormat.SINGLE_ELIMINATION,Instant.now());knockout.participants.addAll(f.tournament.participants);knockout.matches.add(m);knockout.bracket.add(new BracketNode("final",1,0,m.id,m.participantA.participantId(),m.participantB.participantId(),null,null,null,null,null));com.rpsbattle.tournament.scheduler.SingleEliminationScheduler.advance(knockout);assertThat(knockout.bracket.getFirst().winnerParticipantId()).isNotNull();var decided=List.copyOf(knockout.bracket);com.rpsbattle.tournament.scheduler.SingleEliminationScheduler.advance(knockout);assertThat(knockout.bracket).isEqualTo(decided);
            db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),f.job.executionId());assertThat(results.accept(f.machine.id(),f.job.jobId(),f.upload).status()).isEqualTo("ACCEPTED");assertThat(client.call("/internal/workers/"+f.machine.id()+"/jobs/"+f.job.jobId()+"/complete","POST",f.upload,f.machine.enrolled().workerCredential(),false).status()).isEqualTo(200);
            var result=f.upload.result();var different=new RemoteResultService.Result(result.executionId(),result.matchId(),result.winner(),result.loser(),"BOT_FAILED",result.turnCount(),result.durationMs(),result.artifactHashA(),result.artifactHashB(),result.seed(),result.replayChecksum());assertThatThrownBy(()->results.accept(f.machine.id(),f.job.jobId(),new RemoteResultService.Upload(different,f.upload.replayBase64()))).isInstanceOf(AuthException.class).hasMessage("RESULT_CONFLICT");
        }
    }
    @Test void invalidChecksumReplayOwnershipLeaseAndAccountCookieNeverCommitOrReleaseSlot()throws Exception {
        try(var admin=new Browser();var client=new Browser()){
            UUID actor=admin.account(true);var f=fixture(actor);var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");String path="/internal/workers/"+f.machine.id()+"/jobs/"+f.job.jobId()+"/complete";
            assertThat(admin.call(path,"POST",f.upload,null,true).status()).isEqualTo(401);assertThat(client.call(path,"POST",f.upload,other.enrolled().workerCredential(),false).status()).isEqualTo(403);
            assertThatThrownBy(()->results.accept(other.id(),f.job.jobId(),f.upload)).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_ASSIGNED");assertThatThrownBy(()->results.accept(f.machine.id(),f.job.jobId(),new RemoteResultService.Upload(f.upload.result(),Base64.getEncoder().encodeToString("{}".getBytes())))).isInstanceOf(AuthException.class).hasMessage("REPLAY_CHECKSUM_MISMATCH");
            var r=f.upload.result();var changed=new RemoteResultService.Result(r.executionId(),r.matchId(),r.winner(),r.loser(),r.reason(),r.turnCount()+1,r.durationMs(),r.artifactHashA(),r.artifactHashB(),r.seed(),r.replayChecksum());assertThatThrownBy(()->results.accept(f.machine.id(),f.job.jobId(),new RemoteResultService.Upload(changed,f.upload.replayBase64()))).isInstanceOf(AuthException.class);
            db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),f.job.executionId());assertThatThrownBy(()->results.accept(f.machine.id(),f.job.jobId(),f.upload)).isInstanceOf(AuthException.class).hasMessage("EXECUTION_NOT_COMPLETABLE");assertThat(telemetry.view(f.machine.id()).reservedSlots()).isEqualTo(1);assertThat(db.queryForObject("select count(*) from worker_result_receipts where execution_id=?",Integer.class,f.job.executionId())).isZero();
        }
    }
    @Test void projectionFailureRollsBackRamAndDatabaseButRetainsReceiptForRetry()throws Exception {
        try(var admin=new Browser()){
            var f=fixture(admin.account(true));results.accept(f.machine.id(),f.job.jobId(),f.upload);long revision=f.tournament.revision;
            doThrow(new IllegalStateException("fixture disk unavailable")).when(archives).persistRemote(any(),any(),any());
            try{assertThatThrownBy(()->projector.project(f.job.executionId())).isInstanceOf(RuntimeException.class);}finally{doCallRealMethod().when(archives).persistRemote(any(),any(),any());}
            assertThat(f.tournament.revision).isEqualTo(revision);assertThat(f.tournament.matches.getFirst().status).isEqualTo(TournamentMatch.Status.PENDING);assertThat(db.queryForObject("select projection_status from worker_result_receipts where execution_id=?",String.class,f.job.executionId())).isEqualTo("PENDING");
            projector.project(f.job.executionId());assertThat(db.queryForObject("select projection_status from worker_result_receipts where execution_id=?",String.class,f.job.executionId())).isEqualTo("APPLIED");assertThat(archives.load(f.job.match().matchId().toString()).log().events()).isNotEmpty();
        }
    }
}
