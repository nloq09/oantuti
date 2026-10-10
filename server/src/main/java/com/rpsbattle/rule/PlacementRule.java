package com.rpsbattle.rule;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;

/**
 * Determines whether a player is allowed to place a piece of a given type
 * at a given position during the PLACEMENT phase.
 */
public interface PlacementRule {

    /**
     * @param playerId  the player attempting to place
     * @param pieceType the type of piece being placed
     * @param position  the target position
     * @param board     the current board snapshot
     * @return true if placement is legal
     */
    boolean canPlace(PlayerId playerId, PieceType pieceType, Position position, Board board);
}
