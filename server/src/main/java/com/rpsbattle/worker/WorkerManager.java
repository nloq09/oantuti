package com.rpsbattle.worker;

import com.rpsbattle.tournament.TournamentService;
import com.rpsbattle.tournament.domain.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class WorkerManager {
    private final ExecutionQueue queue;
    private final MatchWorker worker;
    private final TournamentService tournaments;
    private final int maxConcurrent;
    private final Executor launches;
    private final Set<String> active = new HashSet<>();
    private boolean draining;
    @Autowired public WorkerManager(ExecutionQueue queue, MatchWorker worker, TournamentService tournaments,
            @Value("${rps.worker.max-concurrent-matches:${rps.tournament.max-concurrent-matches:4}}") int maxConcurrent) {
        this(queue, worker, tournaments, maxConcurrent, Executors.newFixedThreadPool(maxConcurrent,
                task -> new Thread(task, "rps-match-launch")));
    }
    /** Executor injection also permits deterministic concurrency tests using the same implementation. */
    public WorkerManager(ExecutionQueue queue, MatchWorker worker, TournamentService tournaments, int maxConcurrent, Executor launches) {
        if (maxConcurrent < 1 || maxConcurrent > 4) throw new IllegalArgumentException("Invalid match capacity");
        this.queue=queue;this.worker=worker;this.tournaments=tournaments;this.maxConcurrent=maxConcurrent;this.launches=launches;
    }
    public void releaseReservations(Collection<String> ids){queue.release(ids);}
    public void offer(MatchExecutionRequest request) { queue.offer(request); }
    public void dispatch() {
        while (true) {
            MatchExecutionRequest request;
            synchronized (this) {
                if (draining || active.size() >= maxConcurrent) return;
                request = queue.poll(); if (request == null) return;
                active.add(request.match().id);
            }
            try { launches.execute(() -> launch(request)); }
            catch (RejectedExecutionException failure) { fail(request, "SERVER_SHUTDOWN"); }
        }
    }
    private void launch(MatchExecutionRequest request) {
        var t=request.tournament(); var m=request.match();
        synchronized(t) {
            if (t.status!=TournamentStatus.RUNNING || m.status!=TournamentMatch.Status.PENDING || t.matches.stream().noneMatch(entry->entry==m) || draining()) {
                complete(m.id); return;
            }
            if(t.matches.stream().filter(game->game.status==TournamentMatch.Status.RUNNING).count()>=t.maxConcurrentMatches) {complete(m.id);return;}
            if(!t.operations.active(m.participantA.participantId())||!t.operations.active(m.participantB.participantId())||(!t.operations.autoAdvanceRounds()&&m.round>t.operations.releasedRound())||t.operations.reviews().containsKey(m.id)){complete(m.id);return;}
            try {
                var result=Objects.requireNonNull(worker.execute(request));
                m.onlineMatchId=result.roomId();m.historyMatchId=result.historyId();
                m.status=TournamentMatch.Status.RUNNING;if(m.startedAt==null)m.startedAt=Instant.now();
                org.slf4j.LoggerFactory.getLogger(getClass()).info("event=match_started tournamentId={} matchId={} botId={} opponentBotId={}",t.id,m.id,m.participantA.botId(),m.participantB.botId());
                if(t.operations.audit().stream().noneMatch(e->e.type().equals("ROUND_STARTED")&&Integer.toString(m.round).equals(e.target())))com.rpsbattle.tournament.TournamentAudit.append(t,"ROUND_STARTED",null,Integer.toString(m.round),null,null,null);
                com.rpsbattle.tournament.TournamentAudit.append(t,"MATCH_STARTED",null,m.id,null,null,com.rpsbattle.tournament.TournamentAudit.result(m));tournaments.changed(t);
            } catch (RuntimeException failure) { fail(request,"MATCH_START_FAILED"); }
        }
    }
    private void fail(MatchExecutionRequest request, String code) {
        synchronized(request.tournament()) {
            var m=request.match(); m.status=TournamentMatch.Status.FAILED; m.failure=code; m.finishedAt=Instant.now();
            com.rpsbattle.tournament.TournamentAudit.append(request.tournament(),"MATCH_ERROR",null,m.id,code,null,com.rpsbattle.tournament.TournamentAudit.result(m));tournaments.changed(request.tournament()); complete(m.id);
        }
    }
    public synchronized void complete(String id) { active.remove(id); queue.release(List.of(id)); }
    public synchronized int active() { return active.size(); }
    public int pending() { return queue.size(); }
    public synchronized boolean draining() { return draining; }
    @jakarta.annotation.PreDestroy public void drain() {
        synchronized(this) { draining=true; queue.drain(); }
        if (launches instanceof ExecutorService pool) {
            pool.shutdown();
            try { if(!pool.awaitTermination(5,TimeUnit.SECONDS)) pool.shutdownNow(); }
            catch(InterruptedException error) { pool.shutdownNow();Thread.currentThread().interrupt(); }
        }
    }
    public Map<String,Object> status() { return Map.of("state",draining()?"DRAINING":"ACCEPTING","active",active(),"pending",pending(),"maxConcurrentMatches",maxConcurrent,"maxPendingMatches",queue.capacity()); }
}
