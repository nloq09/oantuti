package com.rpsbattle.rule;

/**
 * Aggregates all rule components for a game variant.
 * <p>
 * A RuleSet is a named, self-contained bundle of rules.
 * The Game Engine only talks to RuleSet — it never imports Classic classes.
 */
public interface RuleSet {

    /**
     * Human-readable name of this rule set (e.g. "Classic").
     */
    String name();

    /**
     * Maximum turns before the game ends by turn limit.
     */
    int maxTurns();

    MovementRule movementRule();

    CombatRule combatRule();

    PlacementRule placementRule();

    VictoryRule victoryRule();

    TurnRule turnRule();
}
