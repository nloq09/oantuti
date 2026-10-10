package com.rpsbattle.agent;

import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;

import java.util.Objects;

/**
 * Public-information snapshot for one player. GameState protects its board.
 */
public record PlayerView(GameState state, PlayerId playerId) {
    public PlayerView {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(playerId, "playerId");
        if (state.findPlayer(playerId).isEmpty()) throw new IllegalArgumentException("Unknown player: " + playerId);
    }
}
