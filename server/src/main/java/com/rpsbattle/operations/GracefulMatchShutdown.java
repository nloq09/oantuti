package com.rpsbattle.operations;

import com.rpsbattle.worker.WorkerManager;
import com.rpsbattle.tournament.execution.TournamentExecutionService;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import com.rpsbattle.history.MatchPersistenceCoordinator;
import com.rpsbattle.replay.ReplayArchiveStore;
import com.rpsbattle.online.*;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Runs before bean destruction, while repositories and the DB pool are still alive. */
@Component
public class GracefulMatchShutdown implements SmartLifecycle {
    private final WorkerManager workers;
    private final TournamentExecutionService execution;
    private final OnlineMatchService online;
    private final MatchRegistry rooms;
    private final MatchPersistenceCoordinator history;
    private final TournamentPersistenceCoordinator tournaments;
    private final ReplayArchiveStore replays;
    private volatile boolean running;
    public GracefulMatchShutdown(WorkerManager workers,TournamentExecutionService execution,OnlineMatchService online,
            MatchRegistry rooms,MatchPersistenceCoordinator history,TournamentPersistenceCoordinator tournaments,ReplayArchiveStore replays) {
        this.workers=workers;this.execution=execution;this.online=online;this.rooms=rooms;this.history=history;this.tournaments=tournaments;this.replays=replays;
    }
    public void start(){running=true;}
    public boolean isRunning(){return running;}
    // Below the embedded web server's graceful shutdown phase: first finish HTTP requests, then checkpoint.
    public int getPhase(){return Integer.MAX_VALUE-2048;}
    public void stop(){
        if(!running)return;running=false;
        online.stopAccepting();workers.drain();
        for(var room:rooms.getActiveMatches()) synchronized(room) {if(!room.state().isFinished())room.infrastructureFailure();history.capture(room);}
        execution.checkpointShutdown();
        flush("matches",history::flush);flush("tournaments",tournaments::flush);flush("replays",replays::flush);
        online.shutdown();
    }
    private void flush(String kind,Runnable action) {
        try {action.run();}catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).error("event=shutdown_persistence_failed resource={}",kind);}
    }
}
