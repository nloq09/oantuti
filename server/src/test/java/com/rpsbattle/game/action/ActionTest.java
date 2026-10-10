package com.rpsbattle.game.action;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Action Tests")
class ActionTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    // ========================================================
    // MoveAction
    // ========================================================
    @Nested
    @DisplayName("MoveAction")
    class MoveActionTests {

        @Test
        @DisplayName("creates valid MoveAction with all fields")
        void createsMoveAction() {
            Position from = new Position(5, 4);
            Position to   = new Position(4, 4);
            MoveAction action = MoveAction.of(P1, "R1", from, to);

            assertThat(action.playerId()).isEqualTo(P1);
            assertThat(action.pieceId()).isEqualTo("R1");
            assertThat(action.from()).isEqualTo(from);
            assertThat(action.to()).isEqualTo(to);
        }

        @Test
        @DisplayName("MoveAction implements Action sealed interface")
        void implementsAction() {
            Action action = MoveAction.of(P1, "R1", new Position(5, 4), new Position(4, 4));
            assertThat(action).isInstanceOf(MoveAction.class);
            assertThat(action.playerId()).isEqualTo(P1);
        }

        @Test
        @DisplayName("from == to throws IllegalArgumentException")
        void fromEqualToThrows() {
            Position pos = new Position(3, 3);
            assertThatThrownBy(() -> MoveAction.of(P1, "R1", pos, pos))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null playerId throws NullPointerException")
        void nullPlayerIdThrows() {
            assertThatThrownBy(() -> MoveAction.of(null, "R1",
                    new Position(0, 0), new Position(1, 0)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("MoveAction record equality")
        void recordEquality() {
            MoveAction a = MoveAction.of(P1, "R1", new Position(5, 4), new Position(4, 4));
            MoveAction b = MoveAction.of(P1, "R1", new Position(5, 4), new Position(4, 4));
            assertThat(a).isEqualTo(b);
        }
    }

    // ========================================================
    // PlaceAction
    // ========================================================
    @Nested
    @DisplayName("PlaceAction")
    class PlaceActionTests {

        @Test
        @DisplayName("creates valid PlaceAction")
        void createsPlaceAction() {
            PlaceAction action = PlaceAction.of(P1, "R1", PieceType.ROCK, new Position(7, 3));
            assertThat(action.playerId()).isEqualTo(P1);
            assertThat(action.pieceId()).isEqualTo("R1");
            assertThat(action.pieceType()).isEqualTo(PieceType.ROCK);
            assertThat(action.position()).isEqualTo(new Position(7, 3));
        }

        @Test
        @DisplayName("PlaceAction implements Action")
        void implementsAction() {
            Action action = PlaceAction.of(P2, "S1", PieceType.SCISSORS, new Position(1, 1));
            assertThat(action).isInstanceOf(PlaceAction.class);
            assertThat(action.playerId()).isEqualTo(P2);
        }
    }

    // ========================================================
    // PassAction
    // ========================================================
    @Nested
    @DisplayName("PassAction")
    class PassActionTests {

        @Test
        @DisplayName("creates valid PassAction")
        void createsPassAction() {
            PassAction action = PassAction.of(P1);
            assertThat(action.playerId()).isEqualTo(P1);
        }

        @Test
        @DisplayName("PassAction implements Action")
        void implementsAction() {
            Action action = PassAction.of(P2);
            assertThat(action).isInstanceOf(PassAction.class);
        }

        @Test
        @DisplayName("null playerId throws NullPointerException")
        void nullPlayerIdThrows() {
            assertThatThrownBy(() -> PassAction.of(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ========================================================
    // SurrenderAction
    // ========================================================
    @Nested
    @DisplayName("SurrenderAction")
    class SurrenderActionTests {

        @Test
        @DisplayName("creates valid SurrenderAction")
        void createsSurrenderAction() {
            SurrenderAction action = SurrenderAction.of(P1);
            assertThat(action.playerId()).isEqualTo(P1);
        }

        @Test
        @DisplayName("SurrenderAction implements Action")
        void implementsAction() {
            Action action = SurrenderAction.of(P2);
            assertThat(action).isInstanceOf(SurrenderAction.class);
        }
    }

    // ========================================================
    // Sealed interface — pattern matching
    // ========================================================
    @Nested
    @DisplayName("Sealed interface pattern matching")
    class SealedPatternTests {

        @Test
        @DisplayName("all Action subtypes can be pattern matched exhaustively")
        void patternMatchAllTypes() {
            Action[] actions = {
                    MoveAction.of(P1, "R1", new Position(5, 4), new Position(4, 4)),
                    PlaceAction.of(P1, "R1", PieceType.ROCK, new Position(7, 3)),
                    PassAction.of(P1),
                    SurrenderAction.of(P1)
            };

            for (Action action : actions) {
                // Java 21 pattern matching switch — must be exhaustive
                String type = switch (action) {
                    case MoveAction     m -> "MOVE";
                    case PlaceAction    p -> "PLACE";
                    case PassAction     p -> "PASS";
                    case SurrenderAction s -> "SURRENDER";
                };
                assertThat(type).isNotBlank();
            }
        }
    }
}
