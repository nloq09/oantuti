package com.rpsbattle.game.engine;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceStatus;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.*;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LegalActionGeneratorTest {
    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");
    private final RuleSet rules = new ClassicRuleSet(P1, P2);
    private final LegalActionGenerator generator = new LegalActionGenerator(rules);

    private Piece piece(String id, PlayerId owner, int row, int col) {
        return Piece.alive(id, owner, PieceType.ROCK, new Position(row, col));
    }

    private GameState state(Board board) {
        return GameState.initial("M1", board,
                List.of(Player.of(P1, "A"), Player.of(P2, "B"))).withPhase(GamePhase.PLAYING);
    }

    private GameState state(Piece... pieces) {
        Board board = Board.defaultBoard();
        for (Piece piece : pieces) board.placePiece(piece);
        return state(board);
    }

    private MoveAction move(String id, int fromRow, int fromCol, int toRow, int toCol) {
        return MoveAction.of(P1, id, new Position(fromRow, fromCol), new Position(toRow, toCol));
    }

    @Test
    void middlePieceHasExactlyEightMoves() {
        var actions = generator.getLegalActions(state(piece("a", P1, 5, 5)), P1);
        assertThat(actions).containsExactly(
                move("a", 5, 5, 4, 4), move("a", 5, 5, 4, 5), move("a", 5, 5, 4, 6),
                move("a", 5, 5, 5, 4), move("a", 5, 5, 5, 6),
                move("a", 5, 5, 6, 4), move("a", 5, 5, 6, 5), move("a", 5, 5, 6, 6));
        assertThat(actions).doesNotHaveDuplicates();
        assertThatThrownBy(actions::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void topLeftCornerHasOnlyThreeInsideMoves() {
        var actions = generator.getLegalActions(state(piece("a", P1, 0, 0)), P1);
        assertThat(actions).containsExactly(move("a", 0, 0, 0, 1), move("a", 0, 0, 1, 0), move("a", 0, 0, 1, 1));
    }

    @Test
    void bottomRightCornerHasOnlyThreeInsideMoves() {
        var actions = generator.getLegalActions(state(piece("a", P1, 8, 8)), P1);
        assertThat(actions).containsExactly(move("a", 8, 8, 7, 7), move("a", 8, 8, 7, 8), move("a", 8, 8, 8, 7));
    }

    @Test
    void excludesFriendlyDestinationForEveryPiece() {
        GameState original = state(piece("a", P1, 5, 5), piece("friend", P1, 4, 5));
        var actions = generator.getLegalActions(original, P1);
        assertThat(actions).doesNotContain(move("a", 5, 5, 4, 5));
        assertThat(actions).allSatisfy(action -> {
            MoveAction move = (MoveAction) action;
            assertThat(original.board().getPiece(move.to())).isEmpty();
        });
        assertThat(actions).hasSize(14).doesNotHaveDuplicates();
    }

    @Test
    void includesAttackEvenWhenAttackerWillLose() {
        Piece enemy = Piece.alive("enemy", P2, PieceType.PAPER, new Position(4, 5));
        var actions = generator.getLegalActions(state(piece("a", P1, 5, 5), enemy), P1);
        assertThat(actions).hasSize(8).contains(move("a", 5, 5, 4, 5));
        assertThat(actions).allSatisfy(action -> assertThat(action.playerId()).isEqualTo(P1));
    }

    @Test
    void fullyOccupiedFriendlyBoardHasNoMoves() {
        Board board = new Board(2, 2);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) board.placePiece(piece("p-" + row + col, P1, row, col));
        }
        assertThat(generator.getLegalActions(state(board), P1)).isEmpty();
    }

    @Test
    void playerWithNoPiecesHasNoMoves() {
        assertThat(generator.getLegalActions(state(piece("enemy", P2, 4, 4)), P1)).isEmpty();
    }

    @Test
    void deadPiecesHaveNoMoves() {
        assertThat(generator.getLegalActions(
                state(piece("dead", P1, 4, 4).withStatus(PieceStatus.DEAD)), P1)).isEmpty();
    }

    @Test
    void wrongTurnAndUnknownPlayerHaveNoMoves() {
        GameState original = state(piece("a", P1, 4, 4), piece("b", P2, 1, 1));
        assertThat(generator.getLegalActions(original, P2)).isEmpty();
        assertThat(generator.getLegalActions(original, PlayerId.of("outsider"))).isEmpty();
        assertThat(generator.getLegalActions(original.withCurrentPlayer(P2), P2)).hasSize(8);
    }

    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"CREATED", "PLACEMENT", "READY", "FINISHED"})
    void noMovesOutsidePlayingInClassic(GamePhase phase) {
        assertThat(generator.getLegalActions(state(piece("a", P1, 4, 4)).withPhase(phase), P1)).isEmpty();
    }

    @Test
    void respectsRectangularBoardDimensions() {
        Board board = new Board(3, 5);
        board.placePiece(piece("a", P1, 2, 4));
        assertThat(generator.getLegalActions(state(board), P1))
                .containsExactly(move("a", 2, 4, 1, 3), move("a", 2, 4, 1, 4), move("a", 2, 4, 2, 3));
    }

    @Test
    void orderIsIndependentOfBoardInsertionAndCallsDoNotMutateState() {
        List<Piece> pieces = new ArrayList<>(List.of(
                piece("z", P1, 5, 5), piece("a", P1, 1, 1), piece("enemy", P2, 0, 0)));
        GameState original = state(pieces.toArray(Piece[]::new));
        Collections.reverse(pieces);
        GameState reversed = state(pieces.toArray(Piece[]::new));
        var actions = generator.getLegalActions(original, P1);
        assertThat(actions).isEqualTo(generator.getLegalActions(reversed, P1))
                .isEqualTo(generator.getLegalActions(original, P1)).doesNotHaveDuplicates();
        assertThat(original.sequence()).isZero();
        assertThat(original.turnNumber()).isZero();
        assertThat(original.currentPlayerId()).isEqualTo(P1);
        for (Piece piece : pieces) {
            assertThat(original.board().findById(piece.id()).orElseThrow().position()).isEqualTo(piece.position());
        }
    }

    @Test
    void generatedActionsAreExactlyTheMovesAcceptedByGameEngine() {
        GameState original = state(piece("a", P1, 5, 5), piece("friend", P1, 4, 5), piece("enemy", P2, 5, 6));
        List<Action> accepted = new ArrayList<>();
        GameEngine engine = new GameEngine(rules);
        Board board = original.board();
        for (Piece piece : board.allPieces()) {
            for (int row = 0; row < board.rows(); row++) {
                for (int col = 0; col < board.cols(); col++) {
                    Position to = new Position(row, col);
                    if (to.equals(piece.position())) continue;
                    MoveAction action = MoveAction.of(P1, piece.id(), piece.position(), to);
                    try {
                        engine.applyAction(original, P1, action);
                        accepted.add(action);
                    } catch (InvalidActionException ignored) {
                        // The engine is the independent authority for action acceptance.
                    }
                }
            }
        }
        assertThat(generator.getLegalActions(original, P1)).containsExactlyInAnyOrderElementsOf(accepted);
    }

    @Test
    void alternativeMovementAndTurnRulesControlGeneratedActions() {
        RuleSet variant = new RuleSet() {
            public String name() {
                return "Diagonal";
            }

            public int maxTurns() {
                return rules.maxTurns();
            }

            public MovementRule movementRule() {
                return (piece, from, to, board) -> Math.abs(to.row() - from.row()) == 2
                        && Math.abs(to.col() - from.col()) == 2;
            }

            public CombatRule combatRule() {
                return rules.combatRule();
            }

            public PlacementRule placementRule() {
                return rules.placementRule();
            }

            public VictoryRule victoryRule() {
                return rules.victoryRule();
            }

            public TurnRule turnRule() {
                return new TurnRule() {
                    public boolean canAct(GameState state, PlayerId player, Action action) {
                        return action instanceof MoveAction move && move.to().row() < move.from().row();
                    }

                    public PlayerId nextPlayer(GameState state) {
                        return P1;
                    }
                };
            }
        };
        GameState original = state(piece("a", P1, 4, 4), piece("friend", P1, 2, 2));
        var actions = new LegalActionGenerator(variant).getLegalActions(original, P1);
        assertThat(actions).contains(move("a", 4, 4, 2, 6))
                .doesNotContain(move("a", 4, 4, 2, 2), move("a", 4, 4, 6, 6), move("a", 4, 4, 3, 4));
        GameEngine engine = new GameEngine(variant);
        assertThat(actions).allSatisfy(action -> assertThatCode(() -> engine.applyAction(original, P1, action))
                .doesNotThrowAnyException());
    }
}
