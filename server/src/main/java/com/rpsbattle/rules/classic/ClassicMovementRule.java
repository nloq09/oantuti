package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.rule.MovementRule;

/**
 * Classic movement rule:
 *  - Exactly 1 step orthogonally (UP / DOWN / LEFT / RIGHT).
 *  - Cannot move diagonally.
 *  - Cannot leave the board.
 *  - Cannot move into a cell occupied by a friendly piece.
 *  - Can move into a cell occupied by an enemy (triggers combat).
 */
public final class ClassicMovementRule implements MovementRule {

    @Override
    public boolean canMove(Piece piece, Position from, Position to, Board board) {
        if (!piece.isAlive() || !piece.position().equals(from) || !board.isInside(from)) {
            return false;
        }
        if (board.getPiece(from).filter(p -> p.id().equals(piece.id())
                && p.ownerId().equals(piece.ownerId()) && p.type() == piece.type()
                && p.isAlive()).isEmpty()) {
            return false;
        }
        // 1. Must be inside the board
        if (!board.isInside(to)) return false;

        // 2. Must be exactly 1 step orthogonally
        int dr = Math.abs(to.row() - from.row());
        int dc = Math.abs(to.col() - from.col());
        if (dr + dc != 1) return false;   // not exactly 1 step, or diagonal

        // 3. Cannot enter a cell occupied by a friendly piece
        return board.getPiece(to)
                .map(occupant -> !occupant.ownedBy(piece.ownerId()))
                .orElse(true); // empty cell is fine
    }
}
