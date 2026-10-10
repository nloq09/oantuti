package com.rpsbattle.replay;

import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.game.state.GameState;

import java.util.List;
import java.util.Objects;

public record MatchLog(String matchId, String ruleSet, Long seed, GameState initialState,
                       List<GameEvent> events, GameResult result) {
    public MatchLog {
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(ruleSet);
        Objects.requireNonNull(initialState);
        Objects.requireNonNull(result);
        events = List.copyOf(events);
        if (!matchId.equals(initialState.matchId())) throw new IllegalArgumentException("Match id mismatch");
    }
}
