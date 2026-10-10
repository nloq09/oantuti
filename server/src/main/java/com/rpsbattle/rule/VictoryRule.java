package com.rpsbattle.rule;

import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.game.state.GameState;

import java.util.Optional;

/**
 * Checks whether the game has ended and, if so, what the result is.
 *
 * Called after every action to detect win/draw conditions.
 */
public interface VictoryRule {

    /**
     * Evaluate the current state and return a result if the game is over.
     *
     * @param state the current game state
     * @return an Optional containing the result if the game has ended,
     *         or empty if the game is still ongoing
     */
    Optional<GameResult> checkResult(GameState state);
}
