package com.rpsbattle.game.state;

import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a finished game.
 * <p>
 * If outcome is WIN, winner is present.
 * If outcome is DRAW, winner is empty.
 */
public final class GameResult {

    private final GameOutcome outcome;
    private final PlayerId winner;   // null for DRAW

    private GameResult(GameOutcome outcome, PlayerId winner) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.winner = winner;
    }

    public static GameResult win(PlayerId winner) {
        return new GameResult(GameOutcome.WIN, Objects.requireNonNull(winner, "winner"));
    }

    public static GameResult draw() {
        return new GameResult(GameOutcome.DRAW, null);
    }

    public GameOutcome outcome() {
        return outcome;
    }

    public Optional<PlayerId> winner() {
        return Optional.ofNullable(winner);
    }

    public boolean isDraw() {
        return outcome == GameOutcome.DRAW;
    }

    public boolean isWin() {
        return outcome == GameOutcome.WIN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameResult other)) return false;
        return outcome == other.outcome && Objects.equals(winner, other.winner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, winner);
    }

    @Override
    public String toString() {
        return isDraw() ? "DRAW" : "WIN(" + winner + ")";
    }
}
