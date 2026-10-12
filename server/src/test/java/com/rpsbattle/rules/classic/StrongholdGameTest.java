package com.rpsbattle.rules.classic;

import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.GameEngine;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class StrongholdGameTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");

    @ParameterizedTest
    @CsvSource({"P1,EMPTY,true", "P2,EMPTY,true", "P1,SCISSORS,true", "P2,SCISSORS,true",
            "P1,PAPER,false", "P2,PAPER,false", "P1,ROCK,false", "P2,ROCK,false"})
    void diagonalStrongholdAttackResolvesCombatBeforeVictory(String id, String defense, boolean wins) {
        var attacker = PlayerId.of(id);
        var defender = attacker.equals(P1) ? P2 : P1;
        var target = attacker.equals(P1) ? new Position(0, 8) : new Position(8, 0);
        var from = attacker.equals(P1) ? new Position(1, 7) : new Position(7, 1);
        var board = Board.defaultBoard();
        board.placePiece(Piece.alive("attack", attacker, PieceType.ROCK, from));
        board.placePiece(Piece.alive("reserve-a", attacker, PieceType.PAPER, new Position(4, 3)));
        board.placePiece(Piece.alive("reserve-d", defender, PieceType.PAPER, new Position(4, 5)));
        if (!defense.equals("EMPTY"))
            board.placePiece(Piece.alive("guard", defender, PieceType.valueOf(defense), target));
        var original = GameState.initial("base-test", board,
                List.of(Player.of(P1, "One"), Player.of(P2, "Two")))
                .withPhase(GamePhase.PLAYING).withCurrentPlayer(attacker);
        var transition = new GameEngine(new ClassicRuleSet(P1, P2)).applyAction(original, attacker,
                MoveAction.of(attacker, "attack", from, target));
        assertThat(transition.newState().isFinished()).isEqualTo(wins);
        assertThat(original.board().findById("attack").orElseThrow().position()).isEqualTo(from);
        if (wins) {
            assertThat(transition.newState().result().orElseThrow().winner()).contains(attacker);
            assertThat(transition.events().getLast()).isInstanceOf(GameEvent.GameFinished.class);
        }
    }
}
