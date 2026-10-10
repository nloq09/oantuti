package com.rpsbattle.rules.classic;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.TurnRule;

import java.util.List;

/**
 * Classic turn rule:
 *  - Alternating turns: P1 → P2 → P1 → …
 *  - A player may only act on their own turn.
 *  - Next player is the one who did NOT just act.
 */
public final class ClassicTurnRule implements TurnRule {

    @Override
    public boolean canAct(GameState state, PlayerId player, Action action) {
        // Only the current player may act and the action must belong to that player
        return state.findPlayer(player).isPresent() && !state.isFinished()
                && state.currentPlayerId().equals(player) && action.playerId().equals(player);
    }

    @Override
    public PlayerId nextPlayer(GameState state) {
        List<PlayerId> ids = state.players().stream()
                .map(p -> p.id())
                .toList();

        PlayerId current = state.currentPlayerId();
        int idx = ids.indexOf(current);
        // Cycle to the next player (wraps around for >2 players too)
        int nextIdx = (idx + 1) % ids.size();
        return ids.get(nextIdx);
    }
}
