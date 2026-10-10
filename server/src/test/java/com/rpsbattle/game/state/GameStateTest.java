package com.rpsbattle.game.state;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GameState Tests")
class GameStateTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private Board board;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        board = Board.defaultBoard();
        player1 = Player.of(P1, "Alice");
        player2 = Player.of(P2, "Bob");
    }

    private GameState initialState() {
        return GameState.initial("M1", board, List.of(player1, player2));
    }

    // ========================================================
    // Initialisation
    // ========================================================
    @Nested
    @DisplayName("Initialisation")
    class InitialisationTests {

        @Test
        @DisplayName("initial state has matchId set")
        void hasMatchId() {
            assertThat(initialState().matchId()).isEqualTo("M1");
        }

        @Test
        @DisplayName("initial state phase is CREATED")
        void initialPhaseIsCreated() {
            assertThat(initialState().phase()).isEqualTo(GamePhase.CREATED);
        }

        @Test
        @DisplayName("initial turnNumber is 0")
        void initialTurnIsZero() {
            assertThat(initialState().turnNumber()).isEqualTo(0);
        }

        @Test
        @DisplayName("initial sequence is 0")
        void initialSequenceIsZero() {
            assertThat(initialState().sequence()).isEqualTo(0L);
        }

        @Test
        @DisplayName("initial result is empty (game not finished)")
        void initialResultIsEmpty() {
            assertThat(initialState().result()).isEmpty();
        }

        @Test
        @DisplayName("initial currentPlayer is first player")
        void initialCurrentPlayerIsFirst() {
            assertThat(initialState().currentPlayerId()).isEqualTo(P1);
        }

        @Test
        @DisplayName("requires at least 2 players")
        void requiresTwoPlayers() {
            assertThatThrownBy(() -> GameState.initial("M1", board, List.of(player1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null matchId throws NullPointerException")
        void nullMatchIdThrows() {
            assertThatThrownBy(() -> GameState.initial(null, board, List.of(player1, player2)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ========================================================
    // currentPlayer
    // ========================================================
    @Nested
    @DisplayName("currentPlayer")
    class CurrentPlayerTests {

        @Test
        @DisplayName("currentPlayer() returns Player object for P1")
        void currentPlayerReturnsPlayerObject() {
            GameState state = initialState();
            assertThat(state.currentPlayer().id()).isEqualTo(P1);
            assertThat(state.currentPlayer().name()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("withCurrentPlayer changes the current player")
        void withCurrentPlayerChanges() {
            GameState state = initialState().withCurrentPlayer(P2);
            assertThat(state.currentPlayerId()).isEqualTo(P2);
            assertThat(state.currentPlayer().name()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("changing currentPlayer does not affect turn number")
        void changingCurrentPlayerKeepsTurn() {
            GameState state = initialState()
                    .withIncrementedTurn()
                    .withCurrentPlayer(P2);
            assertThat(state.turnNumber()).isEqualTo(1);
            assertThat(state.currentPlayerId()).isEqualTo(P2);
        }
    }

    // ========================================================
    // turnNumber
    // ========================================================
    @Nested
    @DisplayName("turnNumber")
    class TurnNumberTests {

        @Test
        @DisplayName("withIncrementedTurn increases turn by 1")
        void incrementTurn() {
            GameState state = initialState().withIncrementedTurn();
            assertThat(state.turnNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple increments accumulate correctly")
        void multipleIncrements() {
            GameState state = initialState()
                    .withIncrementedTurn()
                    .withIncrementedTurn()
                    .withIncrementedTurn();
            assertThat(state.turnNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("withTurnNumber sets specific value")
        void withTurnNumber() {
            GameState state = initialState().withTurnNumber(42);
            assertThat(state.turnNumber()).isEqualTo(42);
        }

        @Test
        @DisplayName("incrementing turn does not change sequence")
        void incrementTurnDoesNotChangeSequence() {
            GameState state = initialState().withIncrementedTurn();
            assertThat(state.sequence()).isEqualTo(0L);
        }
    }

    // ========================================================
    // sequence
    // ========================================================
    @Nested
    @DisplayName("sequence")
    class SequenceTests {

        @Test
        @DisplayName("withIncrementedSequence increases sequence by 1")
        void incrementSequence() {
            GameState state = initialState().withIncrementedSequence();
            assertThat(state.sequence()).isEqualTo(1L);
        }

        @Test
        @DisplayName("sequence increments independently of turn")
        void sequenceIndependentOfTurn() {
            GameState state = initialState()
                    .withIncrementedSequence()
                    .withIncrementedSequence()
                    .withIncrementedTurn();
            assertThat(state.sequence()).isEqualTo(2L);
            assertThat(state.turnNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple sequence increments accumulate")
        void multipleSequenceIncrements() {
            GameState state = initialState();
            for (int i = 0; i < 10; i++) {
                state = state.withIncrementedSequence();
            }
            assertThat(state.sequence()).isEqualTo(10L);
        }
    }

    // ========================================================
    // phase transition
    // ========================================================
    @Nested
    @DisplayName("phase transition")
    class PhaseTransitionTests {

        @Test
        @DisplayName("can transition from CREATED to PLACEMENT")
        void createdToPlacement() {
            GameState state = initialState().withPhase(GamePhase.PLACEMENT);
            assertThat(state.phase()).isEqualTo(GamePhase.PLACEMENT);
        }

        @Test
        @DisplayName("can transition from PLACEMENT to READY")
        void placementToReady() {
            GameState state = initialState()
                    .withPhase(GamePhase.PLACEMENT)
                    .withPhase(GamePhase.READY);
            assertThat(state.phase()).isEqualTo(GamePhase.READY);
        }

        @Test
        @DisplayName("can transition from READY to PLAYING")
        void readyToPlaying() {
            GameState state = initialState()
                    .withPhase(GamePhase.PLAYING);
            assertThat(state.isPlaying()).isTrue();
        }

        @Test
        @DisplayName("can transition to FINISHED with a result")
        void transitionToFinished() {
            GameResult result = GameResult.win(P1);
            GameState state = initialState()
                    .withPhase(GamePhase.FINISHED)
                    .withResult(result);

            assertThat(state.isFinished()).isTrue();
            assertThat(state.result()).isPresent();
            assertThat(state.result().get().winner()).contains(P1);
        }

        @Test
        @DisplayName("DRAW result has no winner")
        void drawResultNoWinner() {
            GameState state = initialState()
                    .withPhase(GamePhase.FINISHED)
                    .withResult(GameResult.draw());

            assertThat(state.result()).isPresent();
            assertThat(state.result().get().isDraw()).isTrue();
            assertThat(state.result().get().winner()).isEmpty();
        }
    }

    // ========================================================
    // immutability
    // ========================================================
    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("wither methods return new instances, not mutating original")
        void witherReturnsNewInstance() {
            GameState original = initialState();
            GameState updated = original.withPhase(GamePhase.PLAYING);

            assertThat(original.phase()).isEqualTo(GamePhase.CREATED);
            assertThat(updated.phase()).isEqualTo(GamePhase.PLAYING);
            assertThat(original).isNotSameAs(updated);
        }

        @Test
        @DisplayName("player list from state is unmodifiable")
        void playerListIsUnmodifiable() {
            GameState state = initialState();
            assertThatThrownBy(() -> state.players().add(Player.of(PlayerId.of("P3"), "Charlie")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ========================================================
    // findPlayer / opponents / alivePiecesOf
    // ========================================================
    @Nested
    @DisplayName("Player queries")
    class PlayerQueryTests {

        @Test
        @DisplayName("findPlayer returns player for known id")
        void findPlayerFound() {
            GameState state = initialState();
            assertThat(state.findPlayer(P1)).isPresent().get()
                    .extracting(Player::name).isEqualTo("Alice");
        }

        @Test
        @DisplayName("findPlayer returns empty for unknown id")
        void findPlayerNotFound() {
            GameState state = initialState();
            assertThat(state.findPlayer(PlayerId.of("P99"))).isEmpty();
        }

        @Test
        @DisplayName("opponents of P1 is [P2]")
        void opponentsOfP1() {
            GameState state = initialState();
            List<Player> opponents = state.opponents(P1);
            assertThat(opponents).hasSize(1);
            assertThat(opponents.get(0).id()).isEqualTo(P2);
        }

        @Test
        @DisplayName("alivePiecesOf returns pieces on board for given player")
        void alivePiecesOf() {
            Piece rock = Piece.alive("R1", P1, PieceType.ROCK, new Position(7, 0));
            board.placePiece(rock);
            GameState state = initialState();

            assertThat(state.alivePiecesOf(P1)).hasSize(1);
            assertThat(state.alivePiecesOf(P2)).isEmpty();
        }

        @Test
        @DisplayName("aliveCount returns 0 when no pieces placed")
        void aliveCountZero() {
            assertThat(initialState().aliveCount(P1)).isEqualTo(0);
        }
    }
}
