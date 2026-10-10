package com.rpsbattle.game.board;

import com.rpsbattle.game.piece.Piece;

import java.util.*;

/**
 * Represents the 2-D game board.
 * <p>
 * Board is mutable (it tracks current positions of pieces), but it
 * does NOT contain movement rules, combat rules, or any RPS logic.
 * Board only manages the spatial state of pieces.
 * <p>
 * Coordinates are 0-indexed: row ∈ [0, rows), col ∈ [0, cols).
 */
public final class Board {

    private final int rows;
    private final int cols;

    /**
     * Maps position → piece currently occupying that cell.
     */
    private final Map<Position, Piece> grid;

    /**
     * Maps pieceId → piece, for O(1) lookup by id.
     */
    private final Map<String, Piece> pieceById;

    public Board(int rows, int cols) {
        if (rows <= 0) throw new IllegalArgumentException("rows must be > 0");
        if (cols <= 0) throw new IllegalArgumentException("cols must be > 0");
        this.rows = rows;
        this.cols = cols;
        this.grid = new HashMap<>();
        this.pieceById = new HashMap<>();
    }

    /**
     * Creates a default 9×9 board.
     */
    public static Board defaultBoard() {
        return new Board(9, 9);
    }

    /**
     * Pieces are immutable, so copying the indexes isolates all board mutations.
     */
    public Board copy() {
        Board copy = new Board(rows, cols);
        copy.grid.putAll(grid);
        copy.pieceById.putAll(pieceById);
        return copy;
    }

    // ---- Query ----

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    /**
     * Returns true if the position is within board boundaries.
     */
    public boolean isInside(Position pos) {
        return pos.row() >= 0 && pos.row() < rows
                && pos.col() >= 0 && pos.col() < cols;
    }

    /**
     * Returns the piece at the given position, or empty if none.
     */
    public Optional<Piece> getPiece(Position pos) {
        return Optional.ofNullable(grid.get(pos));
    }

    /**
     * Returns true if no piece occupies the given position.
     */
    public boolean isEmpty(Position pos) {
        return !grid.containsKey(pos);
    }

    /**
     * Returns an unmodifiable snapshot of all pieces currently on the board.
     */
    public Collection<Piece> allPieces() {
        return List.copyOf(grid.values());
    }

    /**
     * Returns a piece by its id, or empty if not on the board.
     */
    public Optional<Piece> findById(String pieceId) {
        return Optional.ofNullable(pieceById.get(pieceId));
    }

    // ---- Mutation ----

    /**
     * Places a piece on the board.
     *
     * @throws IllegalArgumentException if position is outside board.
     * @throws IllegalStateException    if a different piece already occupies the position.
     */
    public void placePiece(Piece piece) {
        Objects.requireNonNull(piece, "piece");
        Position pos = piece.position();

        if (!isInside(pos)) {
            throw new IllegalArgumentException("Position " + pos + " is outside the board");
        }
        Piece sameId = pieceById.get(piece.id());
        if (sameId != null && !sameId.position().equals(pos)) {
            throw new IllegalStateException("Piece id already placed at " + sameId.position());
        }
        if (grid.containsKey(pos)) {
            Piece existing = grid.get(pos);
            if (!existing.id().equals(piece.id())) {
                throw new IllegalStateException(
                        "Position " + pos + " is already occupied by piece " + existing.id());
            }
        }
        grid.put(pos, piece);
        pieceById.put(piece.id(), piece);
    }

    /**
     * Removes the piece at the given position.
     *
     * @return the removed piece.
     * @throws IllegalStateException if no piece is at the position.
     */
    public Piece removePiece(Position pos) {
        Objects.requireNonNull(pos, "pos");
        Piece removed = grid.remove(pos);
        if (removed == null) {
            throw new IllegalStateException("No piece at position " + pos);
        }
        pieceById.remove(removed.id());
        return removed;
    }

    /**
     * Moves the piece at {@code from} to {@code to}.
     * <p>
     * Board does NOT validate movement legality — that is the Rule Engine's job.
     *
     * @throws IllegalStateException    if no piece is at {@code from}.
     * @throws IllegalStateException    if {@code to} is already occupied.
     * @throws IllegalArgumentException if either position is outside the board.
     */
    public Piece movePiece(Position from, Position to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (!isInside(from)) throw new IllegalArgumentException("from " + from + " is outside board");
        if (!isInside(to)) throw new IllegalArgumentException("to " + to + " is outside board");

        Piece piece = grid.remove(from);
        if (piece == null) {
            throw new IllegalStateException("No piece at position " + from);
        }
        if (grid.containsKey(to)) {
            // rollback
            grid.put(from, piece);
            throw new IllegalStateException("Position " + to + " is already occupied");
        }

        Piece moved = piece.withPosition(to);
        grid.put(to, moved);
        pieceById.put(moved.id(), moved);
        return moved;
    }

    /**
     * Replaces the piece at a position (used after combat to update state).
     * Removes old entry and inserts updated piece.
     */
    public void updatePiece(Piece piece) {
        Objects.requireNonNull(piece, "piece");
        if (!isInside(piece.position())) throw new IllegalArgumentException("Position is outside board");
        Piece occupant = grid.get(piece.position());
        if (occupant != null && !occupant.id().equals(piece.id())) {
            throw new IllegalStateException("Position is already occupied by " + occupant.id());
        }
        Piece previous = pieceById.get(piece.id());
        if (previous != null) grid.remove(previous.position());
        grid.put(piece.position(), piece);
        pieceById.put(piece.id(), piece);
    }

    @Override
    public String toString() {
        return "Board{" + rows + "x" + cols + ", pieces=" + grid.size() + "}";
    }
}
