package com.rpsbattle.rules.classic;

import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.SurrenderAction;
import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.VictoryRule;

import java.util.List;
import java.util.Optional;

/**
 * Classic victory rule:
 *
 *  1. If a player has 0 alive pieces → opponent wins.
 *  2. If turn >= maxTurns:
 *       - more pieces → winner
 *       - equal pieces → DRAW
 *
 * This rule reads only from GameState; it does not mutate anything.
 */
public final class ClassicVictoryRule implements VictoryRule {

    private final int maxTurns;

    public ClassicVictoryRule(int maxTurns) {
        if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be > 0");
        this.maxTurns = maxTurns;
    }

    @Override
    public Optional<GameResult> checkResult(GameState state) {
        if (!state.isPlaying()) return Optional.empty();
        List<PlayerId> playerIds = state.players().stream()
                .map(p -> p.id())
                .toList();

        if (playerIds.size() < 2) return Optional.empty();

        PlayerId p1 = playerIds.get(0);
        PlayerId p2 = playerIds.get(1);

        int count1 = state.aliveCount(p1);
        int count2 = state.aliveCount(p2);

        // Elimination
        if (count1 == 0 && count2 == 0) return Optional.of(GameResult.draw());
        if (count1 == 0)                 return Optional.of(GameResult.win(p2));
        if (count2 == 0)                 return Optional.of(GameResult.win(p1));

        // Turn limit
        if (state.turnNumber() >= maxTurns) {
            if (count1 > count2) return Optional.of(GameResult.win(p1));
            if (count2 > count1) return Optional.of(GameResult.win(p2));
            return Optional.of(GameResult.draw());
        }

        return Optional.empty(); // game continues
    }

    @Override
    public Optional<GameResult> checkResult(GameState state, Action action) {
        if (action instanceof SurrenderAction surrender) {
            var opponents = state.opponents(surrender.playerId());
            if (opponents.size() != 1) {
                throw new IllegalStateException("Classic surrender requires exactly two players");
            }
            return Optional.of(GameResult.win(opponents.get(0).id()));
        }
        return checkResult(state);
    }
}
