package com.rpsbattle.game.action;

import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;

/**
 * A player chooses to pass their turn without moving.
 *
 * Whether passing is allowed is decided by TurnRule, not here.
 */
public record PassAction(PlayerId playerId) implements Action {

    public PassAction {
        Objects.requireNonNull(playerId, "playerId");
    }

    public static PassAction of(PlayerId playerId) {
        return new PassAction(playerId);
    }

    @Override
    public String toString() {
        return "PASS[" + playerId + "]";
    }
}
