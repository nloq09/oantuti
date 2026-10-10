package com.rpsbattle.game.action;

import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;

/**
 * A player surrenders the match immediately.
 */
public record SurrenderAction(PlayerId playerId) implements Action {

    public SurrenderAction {
        Objects.requireNonNull(playerId, "playerId");
    }

    public static SurrenderAction of(PlayerId playerId) {
        return new SurrenderAction(playerId);
    }

    @Override
    public String toString() {
        return "SURRENDER[" + playerId + "]";
    }
}
