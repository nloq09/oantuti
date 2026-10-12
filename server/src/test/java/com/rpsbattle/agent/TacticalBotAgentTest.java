package com.rpsbattle.agent;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.piece.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.*;
import com.rpsbattle.rules.classic.ClassicRuleSet;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TacticalBotAgentTest {
    static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    final ClassicRuleSet rules = new ClassicRuleSet(P1, P2);

    Piece piece(String id, PlayerId owner, PieceType type, int row, int col) {
        return Piece.alive(id, owner, type, new Position(row, col));
    }

    PlayerView view(Piece... pieces) {
        Board board = Board.defaultBoard();
        for (var piece : pieces) board.placePiece(piece);
        return new PlayerView(GameState.initial("BOT", board, List.of(Player.of(P1, "Bot"), Player.of(P2, "Enemy")))
                .withPhase(GamePhase.PLAYING), P1);
    }

    Action choose(PlayerView view) {
        return new TacticalBotAgent(rules, 42).chooseAction(view, new LegalActionGenerator(rules).getLegalActions(view.state(), P1));
    }

    @Test
    void capturesWinningMatchupWithoutMutatingSnapshot() {
        var view = view(piece("bot", P1, PieceType.ROCK, 4, 4), piece("prey", P2, PieceType.SCISSORS, 4, 5));
        assertThat(choose(view)).isEqualTo(MoveAction.of(P1, "bot", new Position(4, 4), new Position(4, 5)));
        assertThat(view.state().board().allPieces()).hasSize(2);
        assertThat(view.state().board().findById("bot").orElseThrow().position()).isEqualTo(new Position(4, 4));
    }

    @Test
    void avoidsLosingAttack() {
        var view = view(piece("bot", P1, PieceType.ROCK, 4, 4), piece("danger", P2, PieceType.PAPER, 4, 5));
        assertThat(((MoveAction) choose(view)).to()).isNotEqualTo(new Position(4, 5));
    }

    @Test
    void seesImmediateRecaptureAfterApparentlyWinningAttack() {
        var view = view(piece("bot", P1, PieceType.ROCK, 4, 4), piece("bait", P2, PieceType.SCISSORS, 4, 5), piece("trap", P2, PieceType.PAPER, 3, 5));
        var destination = ((MoveAction) choose(view)).to();
        assertThat(Math.max(Math.abs(destination.row() - 3), Math.abs(destination.col() - 5))).isGreaterThan(1);
    }

    @Test
    void advancesTowardsFavorableOpponent() {
        var view = view(piece("bot", P1, PieceType.ROCK, 8, 4), piece("prey", P2, PieceType.SCISSORS, 0, 4));
        var destination = ((MoveAction) choose(view)).to();
        assertThat(destination.row()).isEqualTo(7);
        assertThat(Math.abs(destination.col() - 4)).isLessThanOrEqualTo(1);
    }

    @Test
    void takesStrongholdInsteadOfChasingMaterial() {
        var view = view(piece("bot", P1, PieceType.ROCK, 1, 7),
                piece("prey", P2, PieceType.SCISSORS, 1, 6),
                piece("reserve", P2, PieceType.PAPER, 3, 3));
        assertThat(((MoveAction) choose(view)).to()).isEqualTo(new Position(0, 8));
    }

    @Test
    void stopsEnemyFromEnteringOwnStronghold() {
        var view = view(piece("bot", P1, PieceType.ROCK, 6, 2),
                piece("invader", P2, PieceType.SCISSORS, 7, 1),
                piece("reserve", P2, PieceType.PAPER, 0, 4));
        assertThat(((MoveAction) choose(view)).to()).isEqualTo(new Position(7, 1));
    }

    @Test
    void handlesMutualEliminationAndPassFallback() {
        var view = view(piece("bot", P1, PieceType.ROCK, 4, 4), piece("enemy", P2, PieceType.ROCK, 4, 5));
        assertThat(((MoveAction) choose(view)).to()).isEqualTo(new Position(4, 5));
        var pass = PassAction.of(P1);
        assertThat(new TacticalBotAgent(rules, 42).chooseAction(view, List.of(pass, SurrenderAction.of(P1)))).isEqualTo(pass);
        assertThatThrownBy(() -> new TacticalBotAgent(rules, 42).chooseAction(view, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedAndActionOrderAreReproducibleAndChoicesRemainLegal() {
        var view = view(piece("bot", P1, PieceType.ROCK, 4, 4), piece("prey", P2, PieceType.SCISSORS, 0, 0));
        var actions = new LegalActionGenerator(rules).getLegalActions(view.state(), P1);
        var reversed = new ArrayList<Action>(actions);
        Collections.reverse(reversed);
        var first = new TacticalBotAgent(rules, 7);
        var second = new TacticalBotAgent(rules, 7);
        for (int turn = 0; turn < 20; turn++) {
            var chosen = first.chooseAction(view, actions);
            assertThat(chosen).isIn(actions).isEqualTo(second.chooseAction(view, reversed));
        }
    }
}
