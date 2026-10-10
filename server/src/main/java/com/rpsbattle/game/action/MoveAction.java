package com.rpsbattle.game.action;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;

/**
 * A player moves one of their pieces from one cell to an adjacent cell.
 * <p>
 * Legality (1-step, orthogonal, in-bounds, not friendly-occupied) is NOT
 * checked here — that is the Rule Engine's responsibility.
 */
public record MoveAction(
        PlayerId playerId,
        String pieceId,
        Position from,
        Position to
) implements Action {

    public MoveAction {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pieceId, "pieceId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("from and to must be different positions");
        }
    }

    /**
     * Convenience factory.
     */
    public static MoveAction of(PlayerId playerId, String pieceId, Position from, Position to) {
        return new MoveAction(playerId, pieceId, from, to);
    }

    @Override
    public String toString() {
        return "MOVE[" + playerId + " | " + pieceId + " " + from + "->" + to + "]";
    }
}
