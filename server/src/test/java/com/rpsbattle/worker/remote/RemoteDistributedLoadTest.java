package com.rpsbattle.worker.remote;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.scheduler.SingleEliminationScheduler;
import com.rpsbattle.match.Match;
import com.rpsbattle.online.*;

import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.replay.ReplayService;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL queue/receipt validation with synthetic in-process agents. No Docker capacity claim. */
@Tag("remote-load")
@Testcontainers(disabledWithoutDocker=true)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rps.worker.lease-reap-millis=3600000","rps.worker.result-project-millis=3600000"})
class RemoteDistributedLoadTest extends RemoteJobClaimTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");}
    @Autowired RemoteResultService results;
    final List<Long> waits=Collections.synchronizedList(new ArrayList<>());
    final Map<UUID,Long> enqueued=new ConcurrentHashMap<>();
    final Map<UUID,Integer> distribution=new ConcurrentHashMap<>();
    final Map<UUID,int[]> remaining=new ConcurrentHashMap<>();
    RemoteResultService.Upload play(RemoteMatchQueue.Assignment job)throws Exception{
        var s=job.match();var room=new OnlineMatch(s.matchId().toString(),MatchMode.HUMAN_VS_HUMAN,s.seed(),Clock.systemUTC(),Duration.ofMillis(s.turnTimeoutMs()));
        com.rpsbattle.agent.PlayerAgent agent=(view,actions)->actions.getFirst();room.attachBot(OnlineMatch.P1,agent,"Synthetic A");room.join();room.attachBot(OnlineMatch.P2,agent,"Synthetic B");room.prepareBots();
        var match=new Match(room.state().withPhase(GamePhase.READY),new com.rpsbattle.rules.online.OnlineRuleSet(OnlineMatch.P1,OnlineMatch.P2,1),agent,agent,s.seed());match.runToCompletion();Path path=Files.createTempFile("load-replay-",".json");byte[] bytes;try{new ReplayService().write(path,match);bytes=Files.readAllBytes(path);}finally{Files.delete(path);}
        remaining.put(job.executionId(),new int[]{match.state().aliveCount(OnlineMatch.P1),match.state().aliveCount(OnlineMatch.P2)});
        String winner=match.state().result().orElseThrow().winner().map(p->p.value()).orElse(null);
        return new RemoteResultService.Upload(new RemoteResultService.Result(job.executionId(),s.matchId(),winner,winner==null?null:winner.equals("P1")?"P2":"P1","ENGINE_RESULT",match.state().turnNumber(),1L,s.botA().artifactHash(),s.botB().artifactHash(),s.seed(),RemoteResultService.hash(bytes)),Base64.getEncoder().encodeToString(bytes));
    }
    @Test void fourThousandEntrantsComplete3999ValidatedReceiptsWithTenWorkersAndEightySlots()throws Exception{
        long start=System.nanoTime();
        try(var admin=new Browser();var pool=Executors.newFixedThreadPool(10)){
            UUID actor=admin.account(true),cup=UUID.randomUUID();var t=new Tournament(cup.toString(),"Synthetic 4000 load",TournamentFormat.SINGLE_ELIMINATION,Instant.now());t.status=TournamentStatus.RUNNING;
            db.update("insert into tournaments(id,owner_user_id,name,format,status,max_participants) values(?,?,'Synthetic 4000 load','SINGLE_ELIMINATION','RUNNING',4000)",cup,actor);
            var bots=new ArrayList<Object[]>();var participants=new ArrayList<Object[]>();for(int i=0;i<4000;i++){UUID bot=UUID.randomUUID(),p=UUID.randomUUID();bots.add(new Object[]{bot,actor});participants.add(new Object[]{p,cup,bot,actor,(long)i});t.participants.add(new TournamentParticipant(p.toString(),bot.toString(),"","Synthetic "+i,i,actor).withSeeding(i+1,null));}
            db.batchUpdate("insert into bots(id,owner_user_id,name,language,source_path,status) values(?,?,'Synthetic','PYTHON','not-executed','READY')",bots);
            db.batchUpdate("insert into tournament_participants(id,tournament_id,bot_id,owner_user_id,display_name,seed) values(?,?,?,?,'Synthetic',?)",participants);
            var workers=new ArrayList<Machine>();for(int i=0;i<10;i++){var m=machine(actor,Set.of("PYTHON"),8,"TRUSTED");workers.add(m);distribution.put(m.id(),0);}
            db.update("insert into worker_pool_policies values(?,'REMOTE_REQUIRED','TRUSTED',80,1)",cup);
            t.matches.addAll(new SingleEliminationScheduler().schedule(t));assertThat(t.matches).hasSize(1952);assertThat(t.bracket).hasSize(4095);int completed=0,peak=0,rounds=0;
            var ref=new RemoteMatchQueue.BotRef(UUID.randomUUID(),"a".repeat(64),"PYTHON","python:3.12-slim");
            while(!SingleEliminationScheduler.complete(t)){
                var round=t.matches.stream().filter(m->m.status==TournamentMatch.Status.PENDING).toList();assertThat(round).isNotEmpty();rounds++;
                var rows=new ArrayList<Object[]>();for(var m:round)rows.add(new Object[]{UUID.fromString(m.id),cup,UUID.fromString(m.participantA.participantId()),UUID.fromString(m.participantB.participantId())});db.batchUpdate("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",rows);
                // Allow a slow development host to validate all eight reserved replays per agent.
                // This harness measures queue/receipt throughput, not the bot execution deadline.
                for(var m:round){UUID job=queue.enqueue(new RemoteMatchQueue.Spec(UUID.fromString(m.id),cup,ref,ref,"1","classic-online-v1",m.participantA.seed()^m.participantB.seed(),1000,300000,1,"TRUSTED"));db.update("insert into worker_pool_routes(job_id) values(?)",job);enqueued.put(job,System.nanoTime());}
                var byId=new HashMap<String,TournamentMatch>();round.forEach(m->byId.put(m.id,m));
                while(round.stream().anyMatch(m->m.status==TournamentMatch.Status.PENDING)){
                    var gate=new CountDownLatch(1);var claims=new ArrayList<Future<RemoteMatchQueue.Batch>>();for(var m:workers)claims.add(pool.submit(()->{gate.await();db.update("update workers set last_heartbeat_at=CURRENT_TIMESTAMP where id=?",m.id());return queue.claim(m.id(),claim(m,8));}));gate.countDown();var assignments=new ArrayList<RemoteMatchQueue.Assignment>();for(var f:claims)assignments.addAll(f.get(60,TimeUnit.SECONDS).jobs());assertThat(assignments).isNotEmpty();assertThat(assignments.stream().map(RemoteMatchQueue.Assignment::executionId).distinct().count()).isEqualTo(assignments.size());int active=db.queryForObject("select count(*) from match_execution_attempts where active_marker=1",Integer.class);peak=Math.max(peak,active);assertThat(active).isLessThanOrEqualTo(80);
                    var tasks=new ArrayList<Future<?>>();for(var machine:workers){var owned=assignments.stream().filter(a->a.workerId().equals(machine.id())).toList();assertThat(owned).hasSizeLessThanOrEqualTo(8);tasks.add(pool.submit(()->{for(var a:owned)try{waits.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-enqueued.get(a.jobId())));queue.start(machine.id(),a.jobId(),new RemoteMatchQueue.Start(a.executionId(),machine.epoch()));var upload=play(a);results.accept(machine.id(),a.jobId(),upload);if(distribution.get(machine.id())==0)results.accept(machine.id(),a.jobId(),upload);distribution.merge(machine.id(),1,Integer::sum);}catch(Exception e){throw new RuntimeException(e);}return null;}));}for(var f:tasks)f.get(120,TimeUnit.SECONDS);
                    for(var a:assignments){var m=byId.get(a.match().matchId().toString());m.status=TournamentMatch.Status.FINISHED;m.result="DRAW";m.remainingA=remaining.get(a.executionId())[0];m.remainingB=remaining.get(a.executionId())[1];db.update("update tournament_matches set status='FINISHED',outcome='DRAW' where id=?",a.match().matchId());completed++;}
                    assertThat(db.queryForObject("select count(*) from match_execution_attempts where active_marker=1",Integer.class)).isZero();
                }
                t.matches.addAll(SingleEliminationScheduler.advance(t));
            }
            assertThat(completed).isEqualTo(3999);assertThat(t.matches).hasSize(3999);assertThat(peak).isEqualTo(80);assertThat(distribution.values()).allMatch(n->n>0);assertThat(db.queryForObject("select count(*) from worker_result_receipts where tournament_id=?",Integer.class,cup)).isEqualTo(3999);assertThat(t.bracket.getLast().winnerParticipantId()).isIn(t.participants.stream().map(TournamentParticipant::participantId).toList());
            double seconds=(System.nanoTime()-start)/1e9;var report=new LinkedHashMap<String,Object>();report.put("mode","SYNTHETIC_CONTROL_PLANE_WITH_REAL_POSTGRES_AND_ONE_TURN_ENGINE_REPLAYS");report.put("dockerMatches",0);report.put("physicalHosts",1);report.put("participants",4000);report.put("firstRoundMatches",1952);report.put("validatedReceipts",completed);report.put("logicalWorkers",10);report.put("peakReservedSlots",peak);report.put("rounds",rounds);report.put("elapsedSeconds",seconds);report.put("validatedReceiptsPerSecond",completed/seconds);report.put("queueWaitP50Ms",WorkerMetricsService.percentile(waits,.5));report.put("queueWaitP95Ms",WorkerMetricsService.percentile(waits,.95));report.put("queueWaitP99Ms",WorkerMetricsService.percentile(waits,.99));report.put("infrastructureFailures",0);report.put("executionsPerWorker",distribution);report.put("cpuRamAndCache","Not measured: synthetic agents do not run Docker or download artifacts");Path reportFile=Path.of(System.getProperty("rps.load.report","build/remote-load.json"));Files.createDirectories(reportFile.getParent());json.writeValue(reportFile.toFile(),report);System.out.println("PASS synthetic 4000/3999, 10 logical workers, peak 80; report="+reportFile);
        }
    }
}
