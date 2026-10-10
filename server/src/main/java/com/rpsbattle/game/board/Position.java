package com.rpsbattle.game.board;

import java.util.Objects;

/**
 * Immutable value object representing a position on the board.
 * row and col are 0-indexed.
 */
public record Position(int row, int col) {

    public Position {
        if (row < 0) throw new IllegalArgumentException("row must be >= 0, got: " + row);
        if (col < 0) throw new IllegalArgumentException("col must be >= 0, got: " + col);
    }

    /**
     * Returns a new Position offset by (dr, dc).
     */
    public Position translate(int dr, int dc) {
        return new Position(row + dr, col + dc);
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}
