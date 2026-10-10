package com.rpsbattle.rule;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;

/**
 * Determines whether a piece is allowed to move from one position to another.
 * <p>
 * Does NOT apply the move — only answers: "is this legal?"
 * All context (board size, friendly pieces, etc.) is passed in explicitly.
 */
public interface MovementRule {

    /**
     * @param piece the piece attempting to move
     * @param from  current position (should equal piece.position())
     * @param to    target position
     * @param board current board snapshot
     * @return true if the move is legal under this rule
     */
    boolean canMove(Piece piece, Position from, Position to, Board board);
}
