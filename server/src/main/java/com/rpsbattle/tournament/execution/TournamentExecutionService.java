package com.rpsbattle.tournament.execution;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.online.*;
import com.rpsbattle.replay.ReplayRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
/** One central admission queue; bot work runs in OnlineMatchService's existing bounded executor. */
@Service
public class TournamentExecutionService {
    private final TournamentRegistry registry;
    private final TournamentService tournaments;
    private final com.rpsbattle.worker.WorkerManager workers;
    private final OnlineMatchService online;
    private final ReplayRegistry replays;
    private final Duration maxDuration;
    public TournamentExecutionService(TournamentRegistry registry,TournamentService tournaments,TournamentMatchExecutor executor,
        OnlineMatchService online,ReplayRegistry replays,@Value("${rps.tournament.max-concurrent-matches:4}") int maxConcurrent,
        @Value("${rps.tournament.match-timeout-seconds:900}") long maxSeconds) {
        this(registry,tournaments,online,replays,maxSeconds,new com.rpsbattle.worker.WorkerManager(new com.rpsbattle.worker.ExecutionQueue(256),new com.rpsbattle.worker.LocalMatchWorker(executor),tournaments,maxConcurrent,Runnable::run));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public TournamentExecutionService(TournamentRegistry registry,TournamentService tournaments,OnlineMatchService online,ReplayRegistry replays,
        @Value("${rps.tournament.match-timeout-seconds:900}") long maxSeconds,com.rpsbattle.worker.WorkerManager workers) {
        if(maxSeconds<1)throw new IllegalArgumentException("Invalid tournament execution limits");
        this.registry=registry;this.tournaments=tournaments;this.workers=workers;this.online=online;this.replays=replays;
        this.maxDuration=Duration.ofSeconds(maxSeconds);
    }
    private void failed(TournamentMatch m,String reason) { m.status=TournamentMatch.Status.FAILED;m.failure=reason;m.finishedAt=Instant.now(); }
    @Scheduled(fixedDelay=200)
    public synchronized void tick() {
        if(workers.draining())return;
        var all=registry.findAll().stream().sorted(Comparator.comparing(t->t.createdAt)).toList();
        for(var t:all) synchronized(t) {
            boolean changed=false;
            for(var m:t.matches) if(m.status==TournamentMatch.Status.RUNNING) {
                try {
                    var room=online.find(m.onlineMatchId);
                    if(room.infrastructureError()!=null)throw new IllegalStateException(room.infrastructureError());
                    if(t.status==TournamentStatus.CANCELLED) { online.release(room.id());failed(m,"CANCELLED");changed=true; }
                    else if(room.state().isFinished()) {
                        replays.save(room,online);
                        var result=room.state().result().orElseThrow();
                        m.winnerParticipantId=result.winner().map(p->p.equals(OnlineMatch.P1)?m.participantA.participantId():m.participantB.participantId()).orElse(null);
                        m.result=result.outcome().name();m.remainingA=room.state().aliveCount(OnlineMatch.P1);m.remainingB=room.state().aliveCount(OnlineMatch.P2);
                        m.failure=room.botError();m.finishedAt=room.finishedAt();m.status=TournamentMatch.Status.FINISHED;
                        online.release(room.id());changed=true;
                    } else if(Instant.now().isAfter(m.startedAt.plus(maxDuration))) { online.release(room.id());failed(m,"MATCH_TIMEOUT");changed=true; }
                } catch(RuntimeException error) { try {online.release(m.onlineMatchId);}catch(RuntimeException ignored){} failed(m,"MATCH_INFRASTRUCTURE_ERROR");changed=true; }
                if(m.status!=TournamentMatch.Status.RUNNING)workers.complete(m.id);
            }
            if(t.status==TournamentStatus.CANCELLED) for(var m:t.matches) if(m.status==TournamentMatch.Status.PENDING){failed(m,"CANCELLED");workers.complete(m.id);changed=true;}
            if(changed)tournaments.changed(t);
        }
        for(var t:all) synchronized(t) {
            if(t.status!=TournamentStatus.RUNNING)continue;
            for(var m:t.matches) if(m.status==TournamentMatch.Status.PENDING) {
                try {workers.offer(new com.rpsbattle.worker.MatchExecutionRequest(t,m));}
                catch(com.rpsbattle.online.DemoRequestException full) {break;}
            }
        }
        workers.dispatch();
        for(var t:all) synchronized(t) {
            if(t.status!=TournamentStatus.RUNNING)continue;
            boolean changed=false;
            if(t.matches.stream().noneMatch(m->m.status==TournamentMatch.Status.PENDING||m.status==TournamentMatch.Status.RUNNING)) {
                t.status=TournamentStatus.FINISHED;t.finishedAt=Instant.now();changed=true;
            }
            if(changed)tournaments.changed(t);
        }
    }
    public synchronized void checkpointShutdown() {
        for(var t:registry.findAll()) synchronized(t) {
            if(t.status==TournamentStatus.RUNNING || t.status==TournamentStatus.READY) {
                t.status=TournamentStatus.RECOVERY_REQUIRED;
                for(var m:t.matches) if(m.status==TournamentMatch.Status.RUNNING) {
                    try {
                        var room=online.find(m.onlineMatchId);
                        if(room.state().isFinished() && room.infrastructureError()==null) {
                            replays.save(room,online);var result=room.state().result().orElseThrow();
                            m.winnerParticipantId=result.winner().map(p->p.equals(OnlineMatch.P1)?m.participantA.participantId():m.participantB.participantId()).orElse(null);
                            m.result=result.outcome().name();m.remainingA=room.state().aliveCount(OnlineMatch.P1);m.remainingB=room.state().aliveCount(OnlineMatch.P2);
                            m.failure=room.botError();m.finishedAt=room.finishedAt();m.status=TournamentMatch.Status.FINISHED;
                        } else failed(m,"SERVER_SHUTDOWN");
                    }catch(RuntimeException missing){failed(m,"SERVER_SHUTDOWN");}
                    workers.complete(m.id);
                }
                if(t.matches.stream().allMatch(m->m.status==TournamentMatch.Status.FINISHED||m.status==TournamentMatch.Status.FAILED)) {t.status=TournamentStatus.FINISHED;t.finishedAt=Instant.now();}
                tournaments.changed(t);
            }
        }
    }
    @PreDestroy public void close(){workers.drain();checkpointShutdown();}
}
