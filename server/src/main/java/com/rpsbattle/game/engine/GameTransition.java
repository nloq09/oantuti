package com.rpsbattle.game.engine;

import com.rpsbattle.game.state.GameState;
import java.util.List;
import java.util.Objects;

public record GameTransition(GameState newState, List<GameEvent> events) {
    public GameTransition {
        Objects.requireNonNull(newState, "newState");
        events = List.copyOf(events);
    }
}
