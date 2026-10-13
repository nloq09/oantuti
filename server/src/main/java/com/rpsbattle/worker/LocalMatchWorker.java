package com.rpsbattle.worker;

import com.rpsbattle.tournament.execution.TournamentMatchExecutor;
import org.springframework.stereotype.Component;

/** Uses the existing engine and bounded bot turn executor, without duplicating game rules. */
@Component
public class LocalMatchWorker implements MatchWorker {
    private final TournamentMatchExecutor executor;
    public LocalMatchWorker(TournamentMatchExecutor executor) { this.executor = executor; }
    public MatchExecutionResult execute(MatchExecutionRequest request) {
        var room = executor.start(request.match());
        return new MatchExecutionResult(room.id(), room.archiveId());
    }
}
