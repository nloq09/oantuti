package com.rpsbattle.game.piece;

/**
 * Lifecycle status of a piece.
 */
public enum PieceStatus {
    /**
     * Piece is active on the board.
     */
    ALIVE,
    /**
     * Piece has been defeated and removed from the board.
     */
    DEAD
}
