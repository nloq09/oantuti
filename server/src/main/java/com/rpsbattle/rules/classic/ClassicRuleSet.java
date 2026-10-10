package com.rpsbattle.rules.classic;

import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.rule.*;

/**
 * The "Classic" rule set — standard RPS Battle rules on a 9×9 board.
 * <p>
 * This is the only class that knows both the rule interfaces AND
 * the concrete Classic implementations. The Game Engine uses only
 * the {@link RuleSet} interface.
 */
public final class ClassicRuleSet implements RuleSet {

    /**
     * Default max turns per the spec.
     */
    public static final int DEFAULT_MAX_TURNS = 200;

    private final ClassicMovementRule movementRule;
    private final ClassicCombatRule combatRule;
    private final ClassicPlacementRule placementRule;
    private final ClassicVictoryRule victoryRule;
    private final ClassicTurnRule turnRule;
    private final int maxTurns;

    /**
     * Standard constructor for a 9×9 classic game.
     *
     * @param player1Id the first player's id (rows 6-8)
     * @param player2Id the second player's id (rows 0-2)
     */
    public ClassicRuleSet(PlayerId player1Id, PlayerId player2Id) {
        this(player1Id, player2Id, DEFAULT_MAX_TURNS);
    }

    /**
     * Constructor with a configurable turn limit (useful for tests).
     */
    public ClassicRuleSet(PlayerId player1Id, PlayerId player2Id, int maxTurns) {
        this.maxTurns = maxTurns;
        this.movementRule = new ClassicMovementRule();
        this.combatRule = new ClassicCombatRule();
        this.placementRule = new ClassicPlacementRule(player1Id, player2Id);
        this.victoryRule = new ClassicVictoryRule(maxTurns);
        this.turnRule = new ClassicTurnRule();
    }

    @Override
    public String name() {
        return "Classic";
    }

    @Override
    public int maxTurns() {
        return maxTurns;
    }

    @Override
    public MovementRule movementRule() {
        return movementRule;
    }

    @Override
    public CombatRule combatRule() {
        return combatRule;
    }

    @Override
    public PlacementRule placementRule() {
        return placementRule;
    }

    @Override
    public VictoryRule victoryRule() {
        return victoryRule;
    }

    @Override
    public TurnRule turnRule() {
        return turnRule;
    }
}
