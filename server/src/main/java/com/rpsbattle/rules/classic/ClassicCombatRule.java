package com.rpsbattle.rules.classic;

import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.rule.CombatResult;
import com.rpsbattle.rule.CombatRule;

/**
 * Classic RPS combat rule:
 * <p>
 * ROCK     beats SCISSORS
 * SCISSORS beats PAPER
 * PAPER    beats ROCK
 * same type → BOTH_DIE
 * <p>
 * The rule is encoded as a data table — no long if/else chain.
 */
public final class ClassicCombatRule implements CombatRule {

    /**
     * Lookup: beats[attacker] = the type that attacker defeats.
     * Using ordinal mapping for O(1) lookup without instanceof checks.
     */
    private static PieceType defeats(PieceType type) {
        return switch (type) {
            case ROCK -> PieceType.SCISSORS;
            case SCISSORS -> PieceType.PAPER;
            case PAPER -> PieceType.ROCK;
        };
    }

    @Override
    public CombatResult resolve(Piece attacker, Piece defender) {
        PieceType a = attacker.type();
        PieceType d = defender.type();

        if (a == d) return CombatResult.BOTH_DIE;
        if (defeats(a) == d) return CombatResult.ATTACKER_WIN;
        return CombatResult.DEFENDER_WIN;
    }
}
