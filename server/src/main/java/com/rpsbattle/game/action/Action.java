package com.rpsbattle.game.action;

import com.rpsbattle.game.player.PlayerId;

/**
 * Sealed interface for all actions a player can take.
 *
 * Using Java 21 sealed interface so the compiler enforces exhaustive
 * pattern matching — no stray subclasses outside this package.
 */
public sealed interface Action
        permits MoveAction, PlaceAction, PassAction, SurrenderAction {

    /** The player who issues this action. */
    PlayerId playerId();
}
