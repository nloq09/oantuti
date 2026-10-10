package com.rpsbattle.game.action;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;

/**
 * A player places a piece onto the board during the PLACEMENT phase.
 */
public record PlaceAction(
        PlayerId playerId,
        String pieceId,
        PieceType pieceType,
        Position position
) implements Action {

    public PlaceAction {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pieceId, "pieceId");
        Objects.requireNonNull(pieceType, "pieceType");
        Objects.requireNonNull(position, "position");
    }

    /**
     * Convenience factory.
     */
    public static PlaceAction of(PlayerId playerId, String pieceId,
                                 PieceType pieceType, Position position) {
        return new PlaceAction(playerId, pieceId, pieceType, position);
    }

    @Override
    public String toString() {
        return "PLACE[" + playerId + " | " + pieceId + "(" + pieceType + ") @" + position + "]";
    }
}
