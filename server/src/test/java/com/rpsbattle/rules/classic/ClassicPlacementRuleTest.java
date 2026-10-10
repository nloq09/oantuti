package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicPlacementRule Tests")
class ClassicPlacementRuleTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private ClassicPlacementRule rule;
    private Board board;

    @BeforeEach
    void setUp() {
        rule  = new ClassicPlacementRule(P1, P2);
        board = Board.defaultBoard();
    }

    @Test
    void rejectsUnknownPlayer() {
        assertThat(rule.canPlace(PlayerId.of("P3"), PieceType.ROCK, new Position(6, 0), board)).isFalse();
    }

    @Test
    void rejectsOutsideBoard() {
        assertThat(rule.canPlace(P1, PieceType.ROCK, new Position(6, 9), board)).isFalse();
        assertThat(rule.canPlace(P1, PieceType.ROCK, new Position(9, 0), board)).isFalse();
    }

    @Nested
    @DisplayName("Player 1 placement (rows 6-8)")
    class Player1Placement {

        @Test @DisplayName("P1 can place at row 6")
        void row6() {
            assertThat(rule.canPlace(P1, PieceType.ROCK, new Position(6, 0), board)).isTrue();
        }

        @Test @DisplayName("P1 can place at row 7")
        void row7() {
            assertThat(rule.canPlace(P1, PieceType.PAPER, new Position(7, 4), board)).isTrue();
        }

        @Test @DisplayName("P1 can place at row 8")
        void row8() {
            assertThat(rule.canPlace(P1, PieceType.SCISSORS, new Position(8, 8), board)).isTrue();
        }

        @Test @DisplayName("P1 cannot place at row 5 (out of zone)")
        void row5Rejected() {
            assertThat(rule.canPlace(P1, PieceType.ROCK, new Position(5, 0), board)).isFalse();
        }

        @Test @DisplayName("P1 cannot place at row 0 (P2 zone)")
        void row0RejectedForP1() {
            assertThat(rule.canPlace(P1, PieceType.ROCK, new Position(0, 0), board)).isFalse();
        }
    }

    @Nested
    @DisplayName("Player 2 placement (rows 0-2)")
    class Player2Placement {

        @Test @DisplayName("P2 can place at row 0")
        void row0() {
            assertThat(rule.canPlace(P2, PieceType.ROCK, new Position(0, 0), board)).isTrue();
        }

        @Test @DisplayName("P2 can place at row 1")
        void row1() {
            assertThat(rule.canPlace(P2, PieceType.SCISSORS, new Position(1, 3), board)).isTrue();
        }

        @Test @DisplayName("P2 can place at row 2")
        void row2() {
            assertThat(rule.canPlace(P2, PieceType.PAPER, new Position(2, 8), board)).isTrue();
        }

        @Test @DisplayName("P2 cannot place at row 3 (out of zone)")
        void row3Rejected() {
            assertThat(rule.canPlace(P2, PieceType.ROCK, new Position(3, 0), board)).isFalse();
        }

        @Test @DisplayName("P2 cannot place at row 8 (P1 zone)")
        void row8RejectedForP2() {
            assertThat(rule.canPlace(P2, PieceType.ROCK, new Position(8, 0), board)).isFalse();
        }
    }

    @Nested
    @DisplayName("Occupied cell")
    class OccupiedCell {

        @Test @DisplayName("cannot place on an already occupied cell")
        void occupiedCellRejected() {
            var existingPiece = com.rpsbattle.game.piece.Piece.alive(
                    "R1", P1, PieceType.ROCK, new Position(7, 0));
            board.placePiece(existingPiece);
            assertThat(rule.canPlace(P1, PieceType.PAPER, new Position(7, 0), board)).isFalse();
        }
    }
}
