package com.rpsbattle.game.board;

import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceStatus;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Board Tests")
class BoardTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private Board board;

    @BeforeEach
    void setUp() {
        board = Board.defaultBoard(); // 9x9
    }

    // ---- Helpers ----

    private Piece rock(String id, PlayerId owner, int row, int col) {
        return Piece.alive(id, owner, PieceType.ROCK, new Position(row, col));
    }

    @Test
    void samePieceIdCannotOccupyTwoCells() {
        Piece original = rock("a", P1, 4, 4);
        board.placePiece(original);
        assertThatThrownBy(() -> board.placePiece(original.withPosition(new Position(4, 5))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(board.allPieces()).hasSize(1);
        assertThat(board.findById("a").orElseThrow().position()).isEqualTo(new Position(4, 4));
    }

    @Test
    void rejectedUpdateKeepsBothIndexesIntact() {
        Piece original = rock("a", P1, 4, 4), other = rock("b", P2, 4, 5);
        board.placePiece(original); board.placePiece(other);
        assertThatThrownBy(() -> board.updatePiece(original.withPosition(other.position()))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> board.updatePiece(original.withPosition(new Position(9, 4)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(board.findById("a").orElseThrow().position()).isEqualTo(original.position());
        assertThat(board.findById("b").orElseThrow().position()).isEqualTo(other.position());
        assertThat(board.getPiece(original.position())).contains(original);
        assertThat(board.getPiece(other.position())).contains(other);
    }

    @Test
    void successfulUpdateMovesIndexAndPieceSnapshotIsIndependent() {
        Piece original = rock("a", P1, 4, 4);
        board.placePiece(original);
        var snapshot = board.allPieces();
        board.updatePiece(original.withPosition(new Position(3, 4)));
        assertThat(board.getPiece(original.position())).isEmpty();
        assertThat(board.findById("a").orElseThrow().position()).isEqualTo(new Position(3, 4));
        assertThat(snapshot.iterator().next().position()).isEqualTo(original.position());
        board.removePiece(new Position(3, 4));
        assertThat(snapshot).hasSize(1);
    }

    // ========================================================
    // isInside
    // ========================================================
    @Nested
    @DisplayName("isInside")
    class IsInside {

        @Test
        @DisplayName("corners should be inside 9x9 board")
        void cornersAreInside() {
            assertThat(board.isInside(new Position(0, 0))).isTrue();
            assertThat(board.isInside(new Position(8, 8))).isTrue();
            assertThat(board.isInside(new Position(0, 8))).isTrue();
            assertThat(board.isInside(new Position(8, 0))).isTrue();
        }

        @Test
        @DisplayName("position (9,0) is outside 9x9 board")
        void rowOutsideIsNotInside() {
            assertThat(board.isInside(new Position(9, 0))).isFalse();
        }

        @Test
        @DisplayName("position (0,9) is outside 9x9 board")
        void colOutsideIsNotInside() {
            assertThat(board.isInside(new Position(0, 9))).isFalse();
        }
    }

    // ========================================================
    // placePiece / getPiece
    // ========================================================
    @Nested
    @DisplayName("placePiece and getPiece")
    class PlaceAndGet {

        @Test
        @DisplayName("placing a piece stores it at the correct position")
        void placePieceStoresAtPosition() {
            Piece r = rock("R1", P1, 3, 4);
            board.placePiece(r);

            Optional<Piece> retrieved = board.getPiece(new Position(3, 4));
            assertThat(retrieved).isPresent().contains(r);
        }

        @Test
        @DisplayName("getPiece returns empty for unoccupied cell")
        void getPieceEmptyForUnoccupiedCell() {
            assertThat(board.getPiece(new Position(5, 5))).isEmpty();
        }

        @Test
        @DisplayName("placing piece outside board throws IllegalArgumentException")
        void placeOutsideBoardThrows() {
            Piece r = rock("R1", P1, 9, 0); // position 9,0 is outside
            assertThatThrownBy(() -> board.placePiece(r))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("two different pieces cannot occupy the same cell")
        void twoPiecesCannotOccupySameCell() {
            Piece r1 = rock("R1", P1, 3, 4);
            Piece r2 = rock("R2", P2, 3, 4);
            board.placePiece(r1);

            assertThatThrownBy(() -> board.placePiece(r2))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("placing same piece again (idempotent update) does not throw")
        void placingSamePieceAgainIsIdempotent() {
            Piece r = rock("R1", P1, 3, 4);
            board.placePiece(r);
            // placing the same id again should not throw (update in place)
            assertThatCode(() -> board.placePiece(r)).doesNotThrowAnyException();
        }
    }

    // ========================================================
    // removePiece
    // ========================================================
    @Nested
    @DisplayName("removePiece")
    class RemovePiece {

        @Test
        @DisplayName("removes piece and cell becomes empty")
        void removesPieceCorrectly() {
            Position pos = new Position(2, 2);
            Piece r = rock("R1", P1, 2, 2);
            board.placePiece(r);
            board.removePiece(pos);

            assertThat(board.getPiece(pos)).isEmpty();
            assertThat(board.isEmpty(pos)).isTrue();
        }

        @Test
        @DisplayName("removePiece returns the removed piece")
        void removeReturnsRemovedPiece() {
            Piece r = rock("R1", P1, 2, 2);
            board.placePiece(r);
            Piece removed = board.removePiece(new Position(2, 2));
            assertThat(removed).isEqualTo(r);
        }

        @Test
        @DisplayName("removing from empty cell throws IllegalStateException")
        void removeFromEmptyCellThrows() {
            assertThatThrownBy(() -> board.removePiece(new Position(5, 5)))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    // ========================================================
    // movePiece
    // ========================================================
    @Nested
    @DisplayName("movePiece")
    class MovePiece {

        @Test
        @DisplayName("piece moves from source to destination")
        void movesPieceToDestination() {
            Position from = new Position(4, 4);
            Position to   = new Position(4, 5);
            Piece r = rock("R1", P1, 4, 4);
            board.placePiece(r);

            board.movePiece(from, to);

            assertThat(board.getPiece(from)).isEmpty();
            assertThat(board.getPiece(to)).isPresent();
            assertThat(board.getPiece(to).get().position()).isEqualTo(to);
        }

        @Test
        @DisplayName("movePiece returns piece with updated position")
        void moveReturnsUpdatedPiece() {
            board.placePiece(rock("R1", P1, 4, 4));
            Piece moved = board.movePiece(new Position(4, 4), new Position(3, 4));
            assertThat(moved.position()).isEqualTo(new Position(3, 4));
        }

        @Test
        @DisplayName("moving from empty cell throws IllegalStateException")
        void moveFromEmptyCellThrows() {
            assertThatThrownBy(() -> board.movePiece(new Position(0, 0), new Position(0, 1)))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("moving to occupied cell throws IllegalStateException")
        void moveToOccupiedCellThrows() {
            board.placePiece(rock("R1", P1, 4, 4));
            board.placePiece(rock("R2", P2, 4, 5));

            assertThatThrownBy(() -> board.movePiece(new Position(4, 4), new Position(4, 5)))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("moving to outside board throws IllegalArgumentException")
        void moveToOutsideBoardThrows() {
            board.placePiece(rock("R1", P1, 0, 0));
            // Position(0, -1) would fail at construction; test moving to boundary+1
            assertThatThrownBy(() -> board.movePiece(new Position(0, 0), new Position(0, 9)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========================================================
    // isEmpty
    // ========================================================
    @Nested
    @DisplayName("isEmpty")
    class IsEmpty {

        @Test
        @DisplayName("board cell is empty before placing a piece")
        void emptyBeforePlacement() {
            assertThat(board.isEmpty(new Position(5, 5))).isTrue();
        }

        @Test
        @DisplayName("board cell is not empty after placing a piece")
        void notEmptyAfterPlacement() {
            board.placePiece(rock("R1", P1, 5, 5));
            assertThat(board.isEmpty(new Position(5, 5))).isFalse();
        }
    }

    // ========================================================
    // Position equality (record-based)
    // ========================================================
    @Nested
    @DisplayName("Position equality")
    class PositionEquality {

        @Test
        @DisplayName("two Positions with same row,col are equal")
        void sameCoordinatesAreEqual() {
            assertThat(new Position(3, 5)).isEqualTo(new Position(3, 5));
        }

        @Test
        @DisplayName("Positions with different coordinates are not equal")
        void differentCoordinatesAreNotEqual() {
            assertThat(new Position(3, 5)).isNotEqualTo(new Position(3, 6));
        }

        @Test
        @DisplayName("Position with negative row throws at construction")
        void negativeRowThrows() {
            assertThatThrownBy(() -> new Position(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Position with negative col throws at construction")
        void negativeColThrows() {
            assertThatThrownBy(() -> new Position(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Position hashCode is consistent with equals")
        void hashCodeConsistentWithEquals() {
            Position a = new Position(4, 7);
            Position b = new Position(4, 7);
            assertThat(a).hasSameHashCodeAs(b);
        }
    }
}
