package com.rpsbattle.rule;

/**
 * Result of a combat resolution between two pieces.
 */
public enum CombatResult {
    /**
     * The attacking piece wins; defender is removed.
     */
    ATTACKER_WIN,
    /**
     * The defending piece wins; attacker is removed.
     */
    DEFENDER_WIN,
    /**
     * Both pieces are removed from the board.
     */
    BOTH_DIE,
    /**
     * Neither piece wins (e.g., a stand-off variant).
     */
    DRAW
}
