package com.rpsbattle.app;

import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.*;
import com.rpsbattle.rules.classic.*;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.io.StringWriter;
import static org.assertj.core.api.Assertions.*;

class LocalGameApplicationTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");

    @Test
    void defaultDeploymentHasCorrectQuotaZonesAndTurn() {
        GameState state = ClassicGameSetup.create("M1", P1, P2);
        var placement = new ClassicRuleSet(P1, P2).placementRule();
        assertThat(state.phase()).isEqualTo(GamePhase.READY);
        assertThat(state.turnNumber()).isZero();
        assertThat(state.sequence()).isEqualTo(36);
        assertThat(state.currentPlayerId()).isEqualTo(P1);
        for (PlayerId owner : new PlayerId[]{P1, P2}) {
            assertThat(state.aliveCount(owner)).isEqualTo(18);
            assertThat(state.findPlayer(owner).orElseThrow().pieceIds()).hasSize(18);
            for (PieceType type : PieceType.values()) {
                assertThat(state.alivePiecesOf(owner).stream().filter(p -> p.type() == type).count()).isEqualTo(6);
            }
            for (var piece : state.alivePiecesOf(owner)) {
                var board = state.board();
                board.removePiece(piece.position());
                assertThat(placement.canPlace(owner, piece.type(), piece.position(), board)).isTrue();
            }
        }
    }

    @Test
    void humansMoveWithBothCoordinateFormatsThenFinishBySurrender() {
        StringWriter output = new StringWriter();
        GameState finalState = LocalGameApplication.run(new StringReader("move A7 A6\na2 a3\nsurrender\n"), output);
        assertThat(finalState.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(finalState.result()).contains(GameResult.win(P2));
        assertThat(finalState.turnNumber()).isEqualTo(3);
        assertThat(finalState.sequence()).isEqualTo(39);
        assertThat(output.toString()).contains("Human vs Human", "R1", "R2", "P1", "P2", "S1", "S2",
                "Result: WIN(P2)", "Remaining: P1=18, P2=18");
    }

    @Test
    void invalidMovesDoNotConsumeTurnAndUserCanRetry() {
        StringWriter output = new StringWriter();
        GameState finalState = LocalGameApplication.run(new StringReader(
                "A7 A5\nA1 A3\nI9 I8\nA7 J7\nA7 A6\nsurrender\n"), output);
        assertThat(finalState.turnNumber()).isEqualTo(2);
        assertThat(finalState.sequence()).isEqualTo(38);
        assertThat(finalState.result()).contains(GameResult.win(P1));
        assertThat(output.toString()).contains("Illegal movement", "opponent", "No piece at I9", "Try again");
    }

    @Test
    void malformedCoordinatesAreRetriedWithoutChangingState() {
        StringWriter output = new StringWriter();
        GameState finalState = LocalGameApplication.run(new StringReader("move A0 B2\nmove ?? B2\nsurrender\n"), output);
        assertThat(finalState.turnNumber()).isEqualTo(1);
        assertThat(finalState.sequence()).isEqualTo(37);
        assertThat(output.toString()).contains("Use coordinates");
    }

    @Test
    void fullConsoleGameCanReachDrawAtTurnLimit() {
        StringWriter output = new StringWriter();
        GameState finalState = LocalGameApplication.run(new StringReader("pass\n".repeat(200)), output);
        assertThat(finalState.turnNumber()).isEqualTo(200);
        assertThat(finalState.result()).contains(GameResult.draw());
        assertThat(output.toString()).contains("Result: DRAW");
    }

    @Test
    void eofStopsCleanlyInsteadOfRetryingForever() {
        assertThatThrownBy(() -> LocalGameApplication.run(new StringReader(""), new StringWriter()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("input ended");
    }
}
