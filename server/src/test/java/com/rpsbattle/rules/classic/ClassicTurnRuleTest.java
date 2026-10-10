package com.rpsbattle.rules.classic;

import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.action.PassAction;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicTurnRule Tests")
class ClassicTurnRuleTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private ClassicTurnRule rule;
    private GameState state;

    @BeforeEach
    void setUp() {
        rule = new ClassicTurnRule();
        Board board = Board.defaultBoard();
        state = GameState.initial("M1", board, List.of(
                Player.of(P1, "Alice"),
                Player.of(P2, "Bob")
        )).withPhase(GamePhase.PLAYING);
    }

    @Test
    @DisplayName("current player (P1) can act")
    void currentPlayerCanAct() {
        PassAction action = PassAction.of(P1);
        assertThat(rule.canAct(state, P1, action)).isTrue();
    }

    @Test
    @DisplayName("non-current player (P2) cannot act when it is P1's turn")
    void nonCurrentPlayerCannotAct() {
        PassAction action = PassAction.of(P2);
        assertThat(rule.canAct(state, P2, action)).isFalse();
    }

    @Test
    @DisplayName("nextPlayer when P1 is current → P2")
    void nextPlayerFromP1() {
        assertThat(rule.nextPlayer(state)).isEqualTo(P2);
    }

    @Test
    @DisplayName("nextPlayer when P2 is current → P1")
    void nextPlayerFromP2() {
        GameState stateWithP2 = state.withCurrentPlayer(P2);
        assertThat(rule.nextPlayer(stateWithP2)).isEqualTo(P1);
    }

    @Test
    @DisplayName("canAct returns false if player id does not match action player id")
    void mismatchedActionPlayerRejected() {
        // P1's turn, but action belongs to P2
        MoveAction action = MoveAction.of(P2, "R1", new Position(5, 5), new Position(4, 5));
        assertThat(rule.canAct(state, P1, action)).isFalse();
    }

    @Test
    void finishedMatchRejectsActions() {
        assertThat(rule.canAct(state.withPhase(GamePhase.FINISHED), P1, PassAction.of(P1))).isFalse();
    }

    @Test
    void unknownPlayerCannotAct() {
        PlayerId unknown = PlayerId.of("unknown");
        assertThat(rule.canAct(state.withCurrentPlayer(unknown), unknown, PassAction.of(unknown))).isFalse();
    }
}
