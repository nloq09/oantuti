package com.rpsbattle.rules.online;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.*;
import com.rpsbattle.rules.classic.ClassicRuleSet;

/** Classic gameplay, with surrender allowed for either online participant. */
public final class OnlineRuleSet implements RuleSet {
    private final ClassicRuleSet classic;
    private final TurnRule turns;
    public OnlineRuleSet(PlayerId p1, PlayerId p2) {
        classic = new ClassicRuleSet(p1, p2);
        turns = new TurnRule() {
            public boolean canAct(GameState state, PlayerId player, Action action) {
                if (action instanceof SurrenderAction) return state.isPlaying()
                        && state.findPlayer(player).isPresent() && player.equals(action.playerId());
                return classic.turnRule().canAct(state, player, action);
            }
            public PlayerId nextPlayer(GameState state) { return classic.turnRule().nextPlayer(state); }
            public boolean advancesTurn(GameState state, Action action) { return classic.turnRule().advancesTurn(state, action); }
        };
    }
    public String name() { return "classic-online-v1"; }
    public int maxTurns() { return classic.maxTurns(); }
    public MovementRule movementRule() { return classic.movementRule(); }
    public CombatRule combatRule() { return classic.combatRule(); }
    public PlacementRule placementRule() { return classic.placementRule(); }
    public VictoryRule victoryRule() { return classic.victoryRule(); }
    public TurnRule turnRule() { return turns; }
}
