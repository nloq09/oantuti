package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicMovementRule Tests")
class ClassicMovementRuleTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private ClassicMovementRule rule;
    private Board board;

    @BeforeEach
    void setUp() {
        rule = new ClassicMovementRule();
        board = Board.defaultBoard(); // 9x9
    }

    private Piece piece(String id, PlayerId owner, int row, int col, PieceType type) {
        return Piece.alive(id, owner, type, new Position(row, col));
    }

    // ---- Legal moves ----
    @Nested
    @DisplayName("Legal moves")
    class LegalMoves {

        @Test
        @DisplayName("move UP by 1 step is allowed")
        void moveUp() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(5, 5), new Position(4, 5), board)).isTrue();
        }

        @Test
        @DisplayName("move DOWN by 1 step is allowed")
        void moveDown() {
            Piece p = piece("R1", P1, 4, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(4, 5), new Position(5, 5), board)).isTrue();
        }

        @Test
        @DisplayName("move LEFT by 1 step is allowed")
        void moveLeft() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(5, 5), new Position(5, 4), board)).isTrue();
        }

        @Test
        @DisplayName("move RIGHT by 1 step is allowed")
        void moveRight() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(5, 5), new Position(5, 6), board)).isTrue();
        }

        @Test
        @DisplayName("can move into enemy cell (triggers combat)")
        void moveIntoEnemy() {
            Piece p1 = piece("R1", P1, 5, 5, PieceType.ROCK);
            Piece p2 = piece("S1", P2, 4, 5, PieceType.SCISSORS);
            board.placePiece(p1);
            board.placePiece(p2);
            assertThat(rule.canMove(p1, new Position(5, 5), new Position(4, 5), board)).isTrue();
        }
    }

    // ---- Illegal moves ----
    @Nested
    @DisplayName("Illegal moves")
    class IllegalMoves {

        @Test
        @DisplayName("diagonal move is not allowed")
        void diagonalMove() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(5, 5), new Position(4, 4), board)).isFalse();
        }

        @Test
        @DisplayName("2-step move is not allowed")
        void twoStepMove() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(5, 5), new Position(3, 5), board)).isFalse();
        }

        @Test
        @DisplayName("move off bottom edge is not allowed")
        void offBottomEdge() {
            Piece p = piece("R1", P1, 8, 0, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, p.position(), new Position(9, 0), board)).isFalse();
        }

        @Test
        @DisplayName("move off right edge is not allowed")
        void offRightEdge() {
            Piece p = piece("R1", P1, 5, 8, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, p.position(), new Position(5, 9), board)).isFalse();
        }

        @Test
        void rejectsMismatchedSource() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            board.placePiece(p);
            assertThat(rule.canMove(p, new Position(3, 5), new Position(4, 5), board)).isFalse();
        }

        @Test
        void rejectsPieceMissingFromBoard() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK);
            assertThat(rule.canMove(p, p.position(), new Position(4, 5), board)).isFalse();
        }

        @Test
        void rejectsDeadPiece() {
            Piece p = piece("R1", P1, 5, 5, PieceType.ROCK)
                    .withStatus(com.rpsbattle.game.piece.PieceStatus.DEAD);
            board.placePiece(p);
            assertThat(rule.canMove(p, p.position(), new Position(4, 5), board)).isFalse();
        }

        @Test
        @DisplayName("cannot move into friendly piece's cell")
        void cannotEnterFriendlyCell() {
            Piece p1 = piece("R1", P1, 5, 5, PieceType.ROCK);
            Piece p2 = piece("P1", P1, 4, 5, PieceType.PAPER);
            board.placePiece(p1);
            board.placePiece(p2);
            assertThat(rule.canMove(p1, new Position(5, 5), new Position(4, 5), board)).isFalse();
        }
    }
}
