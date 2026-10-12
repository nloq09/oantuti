package com.rpsbattle.game.engine;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.*;
import com.rpsbattle.rule.*;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class GameEngineTest {
    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");
    private static final Position FROM = new Position(4, 4);
    private static final Position TO = new Position(3, 4);
    private final GameEngine engine = new GameEngine(new ClassicRuleSet(P1, P2));

    private GameState state(PieceType defender) {
        Board board = Board.defaultBoard();
        board.placePiece(Piece.alive("a", P1, PieceType.ROCK, FROM));
        board.placePiece(Piece.alive("reserve1", P1, PieceType.PAPER, new Position(8, 8)));
        board.placePiece(Piece.alive("reserve2", P2, PieceType.PAPER, new Position(0, 0)));
        if (defender != null) board.placePiece(Piece.alive("d", P2, defender, TO));
        return GameState.initial("M1", board, List.of(Player.of(P1, "A"), Player.of(P2, "B")))
                .withPhase(GamePhase.PLAYING);
    }

    private MoveAction move() {
        return MoveAction.of(P1, "a", FROM, TO);
    }

    @Test
    void movesIntoEmptyCellAndEmitsOrderedEvents() {
        GameState original = state(null);
        GameTransition transition = engine.applyAction(original, P1, move());
        GameState next = transition.newState();
        assertThat(next.board().getPiece(FROM)).isEmpty();
        assertThat(next.board().getPiece(TO)).get().extracting(Piece::id).isEqualTo("a");
        assertThat(next.board().findById("a")).get().extracting(Piece::position).isEqualTo(TO);
        assertThat(next.currentPlayerId()).isEqualTo(P2);
        assertThat(next.turnNumber()).isEqualTo(1);
        assertThat(next.sequence()).isEqualTo(1);
        assertThat(next.phase()).isEqualTo(GamePhase.PLAYING);
        assertThat(transition.events()).containsExactly(
                new GameEvent.PieceMoved(1, "a", FROM, TO),
                new GameEvent.TurnChanged(1, P1, P2, 1));
        assertThat(original.board().getPiece(FROM)).isPresent();
        assertThat(original.board().getPiece(TO)).isEmpty();
        assertThat(original.sequence()).isZero();
        assertThatThrownBy(() -> transition.events().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource({"SCISSORS,true,false,ATTACKER_WIN", "PAPER,false,true,DEFENDER_WIN", "ROCK,false,false,BOTH_DIE"})
    void resolvesCombat(PieceType defender, boolean attackerLives, boolean defenderLives, CombatResult result) {
        GameState original = state(defender);
        GameTransition transition = engine.applyAction(original, P1, move());
        Board board = transition.newState().board();
        assertThat(board.findById("a").isPresent()).isEqualTo(attackerLives);
        assertThat(board.findById("d").isPresent()).isEqualTo(defenderLives);
        assertThat(board.getPiece(FROM)).isEmpty();
        if (attackerLives) assertThat(board.findById("a").orElseThrow().position()).isEqualTo(TO);
        if (defenderLives) assertThat(board.findById("d").orElseThrow().position()).isEqualTo(TO);
        assertThat(transition.events().get(0)).isInstanceOf(GameEvent.CombatOccurred.class);
        assertThat(((GameEvent.CombatOccurred) transition.events().get(0)).result()).isEqualTo(result);
        assertThat(transition.events().stream().filter(GameEvent.PieceRemoved.class::isInstance).count())
                .isEqualTo(result == CombatResult.BOTH_DIE ? 2 : 1);
        assertThat(transition.events()).allMatch(e -> e.sequence() == 1);
        assertThat(original.board().findById("a")).isPresent();
        assertThat(original.board().findById("d")).isPresent();
    }

    private void rejects(GameState original, PlayerId caller, Action action, String reason) {
        var pieces = List.copyOf(original.board().allPieces());
        assertThatThrownBy(() -> engine.applyAction(original, caller, action))
                .isInstanceOf(InvalidActionException.class).hasMessageContaining(reason);
        assertThat(original.board().allPieces()).containsExactlyInAnyOrderElementsOf(pieces);
        assertThat(original.sequence()).isZero();
        assertThat(original.turnNumber()).isZero();
        assertThat(original.currentPlayerId()).isEqualTo(P1);
    }

    @Test
    void rejectsMovingOpponentsPiece() {
        rejects(state(PieceType.PAPER), P1, MoveAction.of(P1, "d", TO, FROM), "opponent");
    }

    @Test
    void rejectsWrongTurn() {
        rejects(state(null), P2, PassAction.of(P2), "turn");
    }

    @Test
    void rejectsSpoofedPlayerAndUnknownPlayer() {
        rejects(state(null), P1, PassAction.of(P2), "caller");
        PlayerId outsider = PlayerId.of("outsider");
        rejects(state(null), outsider, PassAction.of(outsider), "Unknown player");
    }

    @Test
    void rejectsMissingPieceAndStaleSource() {
        rejects(state(null), P1, MoveAction.of(P1, "missing", FROM, TO), "Unknown piece");
        rejects(state(null), P1, MoveAction.of(P1, "a", new Position(5, 4), TO), "Source");
    }

    @ParameterizedTest
    @CsvSource({"2,4", "2,2", "9,4", "8,8"})
    void rejectsIllegalMovement(int row, int col) {
        rejects(state(null), P1, MoveAction.of(P1, "a", FROM, new Position(row, col)), "Illegal movement");
    }

    @Test
    void rejectsAdjacentFriendlyPieceAndDeadAttacker() {
        GameState original = state(null);
        Board board = original.board();
        board.placePiece(Piece.alive("friend", P1, PieceType.SCISSORS, TO));
        rejects(original.withBoard(board), P1, move(), "Illegal movement");
        board.removePiece(FROM);
        board.placePiece(Piece.alive("a", P1, PieceType.ROCK, FROM)
                .withStatus(com.rpsbattle.game.piece.PieceStatus.DEAD));
        rejects(original.withBoard(board), P1, move(), "dead");
    }

    @Test
    void placementRejectsOccupiedCellAndPlayingPhase() {
        GameState original = state(null);
        var placement = PlaceAction.of(P1, "new", PieceType.ROCK, new Position(8, 8));
        rejects(original.withPhase(GamePhase.PLACEMENT), P1, placement, "Illegal placement");
        rejects(original, P1, placement, "phase");
    }

    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"CREATED", "PLACEMENT", "READY", "FINISHED"})
    void rejectsMovesOutsidePlaying(GamePhase phase) {
        rejects(state(null).withPhase(phase), P1, move(), phase == GamePhase.FINISHED ? "finished" : "phase");
    }

    @Test
    void lastEnemyRemovedFinishesGameWithoutChangingPlayer() {
        GameState original = state(PieceType.SCISSORS);
        Board board = original.board();
        board.removePiece(new Position(0, 0));
        GameTransition transition = engine.applyAction(original.withBoard(board), P1, move());
        assertThat(transition.newState().phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(transition.newState().result()).contains(GameResult.win(P1));
        assertThat(transition.newState().turnNumber()).isEqualTo(1);
        assertThat(transition.newState().currentPlayerId()).isEqualTo(P1);
        assertThat(transition.events().get(transition.events().size() - 1))
                .isEqualTo(new GameEvent.GameFinished(1, GameResult.win(P1), 1));
        assertThat(transition.events()).noneMatch(GameEvent.TurnChanged.class::isInstance);
    }

    @Test
    void bothLastPiecesDieProducesDraw() {
        GameState original = state(PieceType.ROCK);
        Board board = original.board();
        board.removePiece(new Position(0, 0));
        board.removePiece(new Position(8, 8));
        GameState next = engine.applyAction(original.withBoard(board), P1, move()).newState();
        assertThat(next.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(next.result()).contains(GameResult.draw());
        assertThat(next.board().allPieces()).isEmpty();
    }

    @Test
    void turnTwoHundredIsEvaluatedAfterCompletedAction() {
        GameState next = engine.applyAction(state(null).withTurnNumber(199), P1, move()).newState();
        assertThat(next.turnNumber()).isEqualTo(200);
        assertThat(next.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(next.result()).contains(GameResult.win(P1));
    }

    @Test
    void placementUpdatesRosterButDoesNotConsumePlayingTurnOrCheckElimination() {
        GameState original = GameState.initial("M1", Board.defaultBoard(),
                List.of(Player.of(P1, "A"), Player.of(P2, "B"))).withPhase(GamePhase.PLACEMENT);
        GameTransition transition = engine.applyAction(original, P1,
                PlaceAction.of(P1, "p1-rock", PieceType.ROCK, new Position(6, 0)));
        GameState next = transition.newState();
        assertThat(next.aliveCount(P1)).isEqualTo(1);
        assertThat(next.currentPlayer().id()).isEqualTo(P2);
        assertThat(next.findPlayer(P1).orElseThrow().pieceIds()).containsExactly("p1-rock");
        assertThat(next.turnNumber()).isZero();
        assertThat(next.sequence()).isEqualTo(1);
        assertThat(next.phase()).isEqualTo(GamePhase.PLACEMENT);
        assertThat(next.result()).isEmpty();
        assertThat(transition.events().get(0)).isInstanceOf(GameEvent.PiecePlaced.class);
        assertThat(original.aliveCount(P1)).isZero();
    }

    @Test
    void rejectsPlacementInWrongZoneOrWithDuplicateId() {
        GameState placement = state(null).withPhase(GamePhase.PLACEMENT);
        rejects(placement, P1, PlaceAction.of(P1, "new", PieceType.ROCK, new Position(0, 1)), "Illegal placement");
        rejects(placement, P1, PlaceAction.of(P1, "a", PieceType.ROCK, new Position(6, 0)), "already exists");
        rejects(placement, P1, PlaceAction.of(P1, " ", PieceType.ROCK, new Position(6, 0)), "blank");
    }

    @Test
    void passAndSurrenderUseRules() {
        GameState passed = engine.applyAction(state(null), P1, PassAction.of(P1)).newState();
        assertThat(passed.currentPlayerId()).isEqualTo(P2);
        assertThat(passed.turnNumber()).isEqualTo(1);
        assertThat(passed.sequence()).isEqualTo(1);
        GameState surrendered = engine.applyAction(passed, P2, SurrenderAction.of(P2)).newState();
        assertThat(surrendered.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(surrendered.result()).contains(GameResult.win(P1));
        assertThat(surrendered.sequence()).isEqualTo(2);
    }

    @Test
    void snapshotsIsolateConstructorBoardAndReturnedBoard() {
        Board source = state(null).board();
        GameState snapshot = GameState.initial("M1", source, List.of(Player.of(P1, "A"), Player.of(P2, "B")));
        source.removePiece(FROM);
        snapshot.board().removePiece(FROM);
        assertThat(snapshot.board().getPiece(FROM)).isPresent();
        GameState withBoard = snapshot.withBoard(source);
        source.removePiece(new Position(8, 8));
        assertThat(withBoard.aliveCount(P1)).isEqualTo(1);
    }

    @Test
    void alternativeRulesControlMovementCombatVictoryAndTurnOrder() {
        RuleSet classic = new ClassicRuleSet(P1, P2);
        RuleSet variant = new RuleSet() {
            public String name() {
                return "Variant";
            }

            public int maxTurns() {
                return 999;
            }

            public MovementRule movementRule() {
                return (piece, from, to, board) -> true;
            }

            public CombatRule combatRule() {
                return (attacker, defender) -> CombatResult.DRAW;
            }

            public PlacementRule placementRule() {
                return classic.placementRule();
            }

            public VictoryRule victoryRule() {
                return state -> Optional.empty();
            }

            public TurnRule turnRule() {
                return new TurnRule() {
                    public boolean canAct(GameState state, PlayerId player, Action action) {
                        return true;
                    }

                    public PlayerId nextPlayer(GameState state) {
                        return state.currentPlayerId();
                    }
                };
            }
        };
        GameEngine custom = new GameEngine(variant);
        GameState original = state(PieceType.SCISSORS).withTurnNumber(199);
        GameTransition draw = custom.applyAction(original, P1, move());
        assertThat(draw.newState().board().findById("a").orElseThrow().position()).isEqualTo(FROM);
        assertThat(draw.newState().board().findById("d")).isPresent();
        assertThat(draw.newState().phase()).isEqualTo(GamePhase.PLAYING);
        assertThat(draw.newState().currentPlayerId()).isEqualTo(P1);
        GameTransition jump = custom.applyAction(original, P1,
                MoveAction.of(P1, "a", FROM, new Position(1, 1)));
        assertThat(jump.newState().board().findById("a").orElseThrow().position()).isEqualTo(new Position(1, 1));
    }

    @Test
    void repeatedInputProducesSameEventsAndStateFields() {
        GameState original = state(PieceType.SCISSORS);
        GameTransition a = engine.applyAction(original, P1, move());
        GameTransition b = engine.applyAction(original, P1, move());
        assertThat(a.events()).isEqualTo(b.events());
        assertThat(a.newState().board().findById("a").orElseThrow().position())
                .isEqualTo(b.newState().board().findById("a").orElseThrow().position());
        assertThat(a.newState().currentPlayerId()).isEqualTo(b.newState().currentPlayerId());
        assertThat(a.newState().sequence()).isEqualTo(b.newState().sequence());
        assertThat(a.newState().turnNumber()).isEqualTo(b.newState().turnNumber());
    }
}
