package com.rpsbattle.rule;

import com.rpsbattle.game.piece.Piece;

/**
 * Resolves the outcome when two pieces occupy the same cell (combat).
 *
 * This interface knows nothing about the board or positions —
 * it only cares about piece types.
 */
public interface CombatRule {

    /**
     * Resolve combat between an attacker and a defender.
     *
     * @param attacker the piece moving into the defender's cell
     * @param defender the piece already occupying the cell
     * @return the combat outcome
     */
    CombatResult resolve(Piece attacker, Piece defender);
}
