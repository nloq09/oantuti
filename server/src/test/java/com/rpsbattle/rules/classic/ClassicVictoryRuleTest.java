package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.game.state.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicVictoryRule Tests")
class ClassicVictoryRuleTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");
    private static final int MAX_TURNS = 10; // small for tests

    private ClassicVictoryRule rule;
    private Board board;

    @BeforeEach
    void setUp() {
        rule = new ClassicVictoryRule(MAX_TURNS);
        board = Board.defaultBoard();
    }

    private GameState stateWithTurn(int turn) {
        return GameState.initial("M1", board, List.of(
                Player.of(P1, "Alice"),
                Player.of(P2, "Bob")
        )).withTurnNumber(turn).withPhase(GamePhase.PLAYING);
    }

    private void place(PlayerId owner, PieceType type, int row, int col) {
        board.placePiece(Piece.alive(owner + "-" + row + col, owner, type, new Position(row, col)));
    }

    @Test
    void defaultLimitEndsAtTwoHundredTurns() {
        place(P1, PieceType.ROCK, 7, 0);
        place(P2, PieceType.PAPER, 1, 0);
        var classic = new ClassicRuleSet(P1, P2).victoryRule();
        assertThat(classic.checkResult(stateWithTurn(199))).isEmpty();
        assertThat(classic.checkResult(stateWithTurn(200))).contains(GameResult.draw());
        assertThat(classic.checkResult(stateWithTurn(201))).contains(GameResult.draw());
    }

    // ---- Still playing ----
    @Nested
    @DisplayName("Game still ongoing")
    class StillOngoing {

        @Test
        @DisplayName("returns empty when both players have pieces and turns remain")
        void noResultYet() {
            place(P1, PieceType.ROCK, 7, 0);
            place(P2, PieceType.SCISSORS, 1, 0);
            assertThat(rule.checkResult(stateWithTurn(5))).isEmpty();
        }
    }

    // ---- Elimination ----
    @Nested
    @DisplayName("Elimination victories")
    class Elimination {

        @Test
        @DisplayName("P2 has no pieces → P1 wins")
        void p1WinsElimination() {
            place(P1, PieceType.ROCK, 7, 0);
            Optional<GameResult> result = rule.checkResult(stateWithTurn(3));
            assertThat(result).isPresent();
            assertThat(result.get().winner()).contains(P1);
        }

        @Test
        @DisplayName("P1 has no pieces → P2 wins")
        void p2WinsElimination() {
            place(P2, PieceType.SCISSORS, 1, 0);
            var result = rule.checkResult(stateWithTurn(3));
            assertThat(result).isPresent();
            assertThat(result.get().winner()).contains(P2);
        }

        @Test
        @DisplayName("both players have no pieces → DRAW")
        void bothEliminatedIsDraw() {
            var result = rule.checkResult(stateWithTurn(3));
            assertThat(result).isPresent();
            assertThat(result.get().isDraw()).isTrue();
        }
    }

    // ---- Turn limit ----
    @Nested
    @DisplayName("Turn limit")
    class TurnLimit {

        @Test
        @DisplayName("P1 has more pieces at turn limit → P1 wins")
        void p1WinsByCount() {
            place(P1, PieceType.ROCK, 7, 0);
            place(P1, PieceType.PAPER, 7, 1);
            place(P2, PieceType.SCISSORS, 1, 0);
            var result = rule.checkResult(stateWithTurn(MAX_TURNS));
            assertThat(result).isPresent();
            assertThat(result.get().winner()).contains(P1);
        }

        @Test
        @DisplayName("P2 has more pieces at turn limit → P2 wins")
        void p2WinsByCount() {
            place(P1, PieceType.ROCK, 7, 0);
            place(P2, PieceType.SCISSORS, 1, 0);
            place(P2, PieceType.PAPER, 1, 1);
            var result = rule.checkResult(stateWithTurn(MAX_TURNS));
            assertThat(result).isPresent();
            assertThat(result.get().winner()).contains(P2);
        }

        @Test
        @DisplayName("equal pieces at turn limit → DRAW")
        void drawAtTurnLimit() {
            place(P1, PieceType.ROCK, 7, 0);
            place(P2, PieceType.SCISSORS, 1, 0);
            var result = rule.checkResult(stateWithTurn(MAX_TURNS));
            assertThat(result).isPresent();
            assertThat(result.get().isDraw()).isTrue();
        }

        @Test
        @DisplayName("one turn before limit with pieces → no result yet")
        void beforeTurnLimit() {
            place(P1, PieceType.ROCK, 7, 0);
            place(P2, PieceType.SCISSORS, 1, 0);
            assertThat(rule.checkResult(stateWithTurn(MAX_TURNS - 1))).isEmpty();
        }
    }
}
