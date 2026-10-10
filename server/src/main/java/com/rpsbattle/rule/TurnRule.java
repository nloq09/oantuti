package com.rpsbattle.rule;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;

/**
 * Controls turn order and determines who is allowed to act.
 */
public interface TurnRule {

    /**
     * Returns true if the given player is allowed to perform the action
     * in the current state.
     *
     * @param state  current game state
     * @param player the player attempting to act
     * @param action the action being attempted
     * @return true if the player may act now
     */
    boolean canAct(GameState state, PlayerId player, Action action);

    /**
     * Returns the id of the player who should act next, given the state
     * after an action has been applied.
     *
     * @param state the post-action game state
     * @return the next player's id
     */
    PlayerId nextPlayer(GameState state);

    /**
     * Whether this action counts toward the match's turn limit.
     */
    default boolean advancesTurn(GameState state, Action action) {
        return true;
    }
}
