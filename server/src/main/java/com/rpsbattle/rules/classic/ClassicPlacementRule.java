package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.rule.PlacementRule;

/**
 * Classic placement rule:
 * <p>
 * Default: P1 below the top-left to bottom-right diagonal, P2 above it.
 * Diagonal cells and both strongholds are excluded during deployment.
 * <p>
 * The rule is configured at construction time so it can be adapted
 * to other board sizes without subclassing.
 */
public final class ClassicPlacementRule implements PlacementRule {

    private final PlayerId player1Id;
    private final PlayerId player2Id;
    private boolean diagonal;

    /**
     * Inclusive row range for player 1 placement.
     */
    private final int p1RowMin;
    private final int p1RowMax;

    /**
     * Inclusive row range for player 2 placement.
     */
    private final int p2RowMin;
    private final int p2RowMax;

    /**
     * Standard 9×9 constructor.
     */
    public ClassicPlacementRule(PlayerId player1Id, PlayerId player2Id) {
        this(player1Id, player2Id, 6, 8, 0, 2);
        diagonal = true;
    }

    /**
     * Configurable constructor — useful for tests or alternative board sizes.
     */
    public ClassicPlacementRule(
            PlayerId player1Id, PlayerId player2Id,
            int p1RowMin, int p1RowMax,
            int p2RowMin, int p2RowMax) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.p1RowMin = p1RowMin;
        this.p1RowMax = p1RowMax;
        this.p2RowMin = p2RowMin;
        this.p2RowMax = p2RowMax;
    }

    @Override
    public boolean canPlace(PlayerId playerId, PieceType pieceType,
                            Position position, Board board) {
        // Cell must be inside the board and currently empty
        if (!board.isInside(position)) return false;
        if (!board.isEmpty(position)) return false;
        // Both strongholds must remain empty throughout initial deployment.
        if (position.equals(new Position(board.rows() - 1, 0))
                || position.equals(new Position(0, board.cols() - 1))) return false;

        int row = position.row();
        if (diagonal) {
            long side = (long) row * (board.cols() - 1) - (long) position.col() * (board.rows() - 1);
            if (playerId.equals(player1Id)) return side > 0;
            if (playerId.equals(player2Id)) return side < 0;
            return false;
        }

        if (playerId.equals(player1Id)) {
            return row >= p1RowMin && row <= p1RowMax;
        }
        if (playerId.equals(player2Id)) {
            return row >= p2RowMin && row <= p2RowMax;
        }
        return false; // unknown player
    }
}
