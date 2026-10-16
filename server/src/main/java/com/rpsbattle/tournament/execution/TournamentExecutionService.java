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
    private com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator persistence;
    @org.springframework.beans.factory.annotation.Autowired public void persistence(com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator persistence){this.persistence=persistence;}
    private boolean commit(Tournament t){
        if(persistence==null)return true;
        try{persistence.persistNow(t);return true;}catch(RuntimeException failure){
            if(t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED)TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());
            TournamentAudit.append(t,"PERSISTENCE_ERROR",null,t.id,"COMMIT_FAILED",null,null);t.revision++;return false;
        }
    }
    private TournamentReadyService readiness;
    @org.springframework.beans.factory.annotation.Autowired public void readiness(TournamentReadyService readiness){this.readiness=readiness;}
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
            boolean changed=false;t.deferNotifications=true;
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
                        m.moveNanosA=room.botMoveNanos(OnlineMatch.P1);m.moveNanosB=room.botMoveNanos(OnlineMatch.P2);m.moveCountA=room.botMoveCount(OnlineMatch.P1);m.moveCountB=room.botMoveCount(OnlineMatch.P2);
                        m.failure=room.botError();m.finishedAt=room.finishedAt();m.status=TournamentMatch.Status.FINISHED;
                        online.release(room.id());changed=true;
                    } else if(Instant.now().isAfter(m.startedAt.plus(maxDuration))) { online.release(room.id());failed(m,"MATCH_TIMEOUT");changed=true; }
                } catch(RuntimeException error) { try {online.release(m.onlineMatchId);}catch(RuntimeException ignored){} failed(m,"MATCH_INFRASTRUCTURE_ERROR");changed=true; }
                if(m.status!=TournamentMatch.Status.RUNNING){workers.complete(m.id);TournamentAudit.append(t,m.status==TournamentMatch.Status.FINISHED?"MATCH_FINISHED":"MATCH_ERROR",null,m.id,m.failure,null,TournamentAudit.result(m));}
            }
            if(t.status==TournamentStatus.CANCELLED) for(var m:t.matches) if(m.status==TournamentMatch.Status.PENDING){failed(m,"CANCELLED");workers.complete(m.id);changed=true;}
            if(t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED)changed|=TournamentAdministrationService.settleForfeits(t);
            changed|=TournamentSeries.advance(t);
            changed|=com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.qualify(t);
            if((t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED) && com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.hasKnockout(t)) {
                var before=List.copyOf(t.bracket);var next=com.rpsbattle.tournament.scheduler.SingleEliminationScheduler.advance(t);t.matches.addAll(next);
                changed|=!before.equals(t.bracket)||!next.isEmpty();
                if(t.matches.stream().anyMatch(m->m.status==TournamentMatch.Status.FAILED)) {TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());changed=true;}
            }
            if(t.format==TournamentFormat.GROUP_KNOCKOUT&&(t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED)&&t.matches.stream().anyMatch(m->m.status==TournamentMatch.Status.FAILED)){TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());changed=true;}
            if(t.status==TournamentStatus.CANCELLED || t.status==TournamentStatus.RECOVERY_REQUIRED)workers.releaseReservations(t.bracket.stream().map(BracketNode::matchId).filter(Objects::nonNull).toList());
            workers.releaseReservations(t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED||m.status==TournamentMatch.Status.FAILED).map(m->m.id).toList());
            workers.releaseReservations(t.bracket.stream().filter(node->node.resolution()!=null).map(BracketNode::matchId).filter(Objects::nonNull).toList());
            boolean saved=true;if(changed){tournaments.changed(t);saved=commit(t);}t.deferNotifications=false;
            if(changed&&saved)tournaments.notifyChanged(t);
        }
        for(var t:all) synchronized(t) {
            if(t.status!=TournamentStatus.RUNNING)continue;
            int round=t.matches.stream().filter(m->m.status==TournamentMatch.Status.PENDING||m.status==TournamentMatch.Status.RUNNING).mapToInt(m->m.round).min().orElse(0);
            int slots=t.maxConcurrentMatches-(int)t.matches.stream().filter(m->m.status==TournamentMatch.Status.RUNNING).count();
            for(var m:t.matches) if(m.status==TournamentMatch.Status.PENDING && m.round==round && (t.operations.autoAdvanceRounds()||m.round<=t.operations.releasedRound()) && !t.operations.reviews().containsKey(m.id) && slots>0 && (readiness==null||readiness.permit(t,m)) && slots-->0) {
                try {workers.offer(new com.rpsbattle.worker.MatchExecutionRequest(t,m));}
                catch(com.rpsbattle.online.DemoRequestException full) {break;}
            }
        }
        workers.dispatch();
        for(var t:all) synchronized(t) {
            if(t.status!=TournamentStatus.RUNNING)continue;
            boolean changed=false;
            if(t.operations.reviews().isEmpty() && t.matches.stream().noneMatch(m->m.status==TournamentMatch.Status.PENDING||m.status==TournamentMatch.Status.RUNNING) && com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.complete(t)) {
                var target=t.matches.stream().anyMatch(m->m.status==TournamentMatch.Status.FAILED)?TournamentStatus.RECOVERY_REQUIRED:TournamentStatus.FINISHED;
                TournamentStateMachine.transition(t,target,Instant.now());TournamentAudit.append(t,"TOURNAMENT_"+target,null,t.id,null,null,target.name());changed=true;
            }
            if(changed){t.deferNotifications=true;tournaments.changed(t);boolean saved=commit(t);t.deferNotifications=false;if(saved)tournaments.notifyChanged(t);}
        }
    }
    public synchronized void checkpointShutdown() {
        for(var t:registry.findAll()) synchronized(t) {
            if(t.status==TournamentStatus.RUNNING || t.status==TournamentStatus.PAUSED || (t.status==TournamentStatus.READY && !t.matches.isEmpty())) {
                TournamentAudit.append(t,"TOURNAMENT_RECOVERY_REQUIRED",null,t.id,"SERVER_SHUTDOWN",t.status.name(),"RECOVERY_REQUIRED");
                TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());
                for(var m:t.matches) if(m.status==TournamentMatch.Status.RUNNING) {
                    try {
                        var room=online.find(m.onlineMatchId);
                        if(room.state().isFinished() && room.infrastructureError()==null) {
                            replays.save(room,online);var result=room.state().result().orElseThrow();
                            m.winnerParticipantId=result.winner().map(p->p.equals(OnlineMatch.P1)?m.participantA.participantId():m.participantB.participantId()).orElse(null);
                            m.result=result.outcome().name();m.remainingA=room.state().aliveCount(OnlineMatch.P1);m.remainingB=room.state().aliveCount(OnlineMatch.P2);
                            m.moveNanosA=room.botMoveNanos(OnlineMatch.P1);m.moveNanosB=room.botMoveNanos(OnlineMatch.P2);m.moveCountA=room.botMoveCount(OnlineMatch.P1);m.moveCountB=room.botMoveCount(OnlineMatch.P2);
                        m.failure=room.botError();m.finishedAt=room.finishedAt();m.status=TournamentMatch.Status.FINISHED;
                        } else failed(m,"SERVER_SHUTDOWN");
                    }catch(RuntimeException missing){failed(m,"SERVER_SHUTDOWN");}
                    workers.complete(m.id);
                    TournamentAudit.append(t,m.status==TournamentMatch.Status.FINISHED?"MATCH_FINISHED":"MATCH_ERROR",null,m.id,m.failure,null,TournamentAudit.result(m));
                }
                if(com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.hasKnockout(t))t.matches.addAll(com.rpsbattle.tournament.scheduler.SingleEliminationScheduler.advance(t));
                if(t.operations.reviews().isEmpty()&&com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.complete(t) && !t.matches.isEmpty() && t.matches.stream().allMatch(m->m.status==TournamentMatch.Status.FINISHED) && (t.bestOf==1||TournamentSeries.roots(t).stream().allMatch(m->TournamentSeries.decision(t,m).isPresent()))) {TournamentStateMachine.transition(t,TournamentStatus.FINISHED,Instant.now());TournamentAudit.append(t,"TOURNAMENT_FINISHED",null,t.id,null,null,"FINISHED");}
                tournaments.changed(t);
            }
        }
    }
    @PreDestroy public void close(){workers.drain();checkpointShutdown();}
}
