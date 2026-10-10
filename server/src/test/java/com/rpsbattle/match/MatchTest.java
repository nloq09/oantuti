package com.rpsbattle.match;

import com.rpsbattle.agent.*;
import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.engine.InvalidActionException;
import com.rpsbattle.game.piece.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.*;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.io.StringReader;
import java.io.StringWriter;
import static org.assertj.core.api.Assertions.*;

class MatchTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    private final PlayerAgent first = (view, actions) -> actions.get(0);

    private GameState state() {
        Board board = Board.defaultBoard();
        board.placePiece(Piece.alive("a", P1, PieceType.ROCK, new Position(6, 4)));
        board.placePiece(Piece.alive("b", P2, PieceType.PAPER, new Position(2, 4)));
        return GameState.initial("M1", board, List.of(Player.of(P1, "A"), Player.of(P2, "B")))
                .withPhase(GamePhase.READY);
    }

    @Test
    void startsAndAlternatesAgentsWithCorrectViewsAndLegalActions() {
        List<PlayerId> called = new ArrayList<>();
        PlayerAgent agent = (view, actions) -> {
            called.add(view.playerId());
            assertThat(view.state().currentPlayerId()).isEqualTo(view.playerId());
            assertThat(actions).isNotEmpty().allMatch(a -> a.playerId().equals(view.playerId()));
            assertThatThrownBy(actions::clear).isInstanceOf(UnsupportedOperationException.class);
            return actions.get(0);
        };
        Match match = new Match(state(), new ClassicRuleSet(P1, P2), agent, agent);
        assertThat(match.matchId()).isEqualTo("M1");
        assertThat(match.status()).isEqualTo(MatchStatus.CREATED);
        assertThatThrownBy(match::playTurn).isInstanceOf(IllegalStateException.class);
        match.start();
        assertThat(match.state().phase()).isEqualTo(GamePhase.PLAYING);
        assertThatThrownBy(match::start).isInstanceOf(IllegalStateException.class);
        match.playTurn();
        match.playTurn();
        assertThat(called).containsExactly(P1, P2);
        assertThat(match.state().sequence()).isEqualTo(2);
        assertThat(match.state().turnNumber()).isEqualTo(2);
    }

    @Test
    void twoSeededBotsFinishAndReproduceEntireActionHistory() {
        List<Action> historyA = new ArrayList<>(), historyB = new ArrayList<>();
        Match a = seeded(historyA), b = seeded(historyB);
        GameState finalA = a.runToCompletion(), finalB = b.runToCompletion();
        assertThat(historyA).isNotEmpty().isEqualTo(historyB);
        assertThat(finalA.result()).isPresent().isEqualTo(finalB.result());
        assertThat(finalA.turnNumber()).isBetween(1, 30).isEqualTo(finalB.turnNumber());
        assertThat(finalA.sequence()).isEqualTo(finalA.turnNumber());
        assertThat(finalA.aliveCount(P1)).isEqualTo(finalB.aliveCount(P1));
        assertThat(finalA.aliveCount(P2)).isEqualTo(finalB.aliveCount(P2));
        assertThat(a.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(a.runToCompletion()).isSameAs(finalA);
        assertThatThrownBy(a::playTurn).isInstanceOf(IllegalStateException.class);
    }

    private Match seeded(List<Action> history) {
        RandomBotAgent a = new RandomBotAgent(42), b = new RandomBotAgent(99);
        PlayerAgent agentA = (view, actions) -> { Action action = a.chooseAction(view, actions); history.add(action); return action; };
        PlayerAgent agentB = (view, actions) -> { Action action = b.chooseAction(view, actions); history.add(action); return action; };
        return new Match(state(), new ClassicRuleSet(P1, P2, 30), agentA, agentB);
    }

    @Test
    void invalidAgentActionLeavesMatchUnchangedAndCanBeRetried() {
        int[] calls = {0};
        PlayerAgent agent = (view, actions) -> calls[0]++ == 0 ? PassAction.of(P2) : actions.get(0);
        Match match = new Match(state(), new ClassicRuleSet(P1, P2), agent, first);
        match.start();
        GameState before = match.state();
        assertThatThrownBy(match::playTurn).isInstanceOf(InvalidActionException.class);
        assertThat(match.state()).isSameAs(before);
        assertThat(match.status()).isEqualTo(MatchStatus.RUNNING);
        match.playTurn();
        assertThat(match.state().sequence()).isEqualTo(1);
    }

    @Test
    void nullAgentActionIsRejected() {
        Match match = new Match(state(), new ClassicRuleSet(P1, P2), (v, a) -> null, first);
        match.start();
        assertThatThrownBy(match::playTurn).isInstanceOf(InvalidActionException.class).hasMessageContaining("no action");
        assertThat(match.state().sequence()).isZero();
    }

    @Test
    void blockedPlayerReceivesPassAndMatchStillReachesTurnLimit() {
        Board board = new Board(1, 3);
        board.placePiece(Piece.alive("a", P1, PieceType.ROCK, new Position(0, 0)));
        board.placePiece(new Piece("block", P1, PieceType.ROCK, new Position(0, 1), PieceStatus.DEAD));
        board.placePiece(Piece.alive("b", P2, PieceType.PAPER, new Position(0, 2)));
        GameState initial = state().withBoard(board);
        PlayerAgent blocked = (view, actions) -> {
            assertThat(actions).containsExactly(PassAction.of(P1));
            return actions.get(0);
        };
        Match match = new Match(initial, new ClassicRuleSet(P1, P2, 1), blocked, first);
        assertThat(match.runToCompletion().result()).contains(GameResult.draw());
        assertThat(match.state().turnNumber()).isEqualTo(1);
    }

    @Test
    void surrenderFinishesMatchAndDoesNotAskOpponent() {
        Match match = new Match(state(), new ClassicRuleSet(P1, P2),
                (view, actions) -> SurrenderAction.of(view.playerId()),
                (view, actions) -> { throw new AssertionError("Opponent should not act"); });
        assertThat(match.runToCompletion().result()).contains(GameResult.win(P2));
        assertThat(match.status()).isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void consoleHumanAndBotUseTheSameMatchEngine() {
        ConsoleHumanAgent human = new ConsoleHumanAgent(new StringReader("1\nsurrender\n"), new StringWriter());
        Match match = new Match(state(), new ClassicRuleSet(P1, P2), human, new RandomBotAgent(55));
        assertThat(match.runToCompletion().result()).contains(GameResult.win(P2));
        assertThat(match.state().turnNumber()).isEqualTo(3);
    }

    @Test
    void alreadyDecidedBoardFinishesAtStartWithoutCallingAgents() {
        GameState initial = state();
        Board board = initial.board();
        board.removePiece(new Position(2, 4));
        PlayerAgent never = (v, a) -> { throw new AssertionError("No turn expected"); };
        Match match = new Match(initial.withBoard(board), new ClassicRuleSet(P1, P2), never, never);
        assertThat(match.runToCompletion().result()).contains(GameResult.win(P1));
        assertThat(match.state().sequence()).isZero();
    }

    @Test
    void rejectsUnpreparedStateAndDuplicatePlayers() {
        assertThatThrownBy(() -> new Match(state().withPhase(GamePhase.CREATED), new ClassicRuleSet(P1, P2), first, first))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Match(state().withPlayers(List.of(Player.of(P1, "A"), Player.of(P1, "B"))),
                new ClassicRuleSet(P1, P2), first, first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentCannotMutateMatchThroughPlayerView() {
        PlayerAgent agent = (view, actions) -> {
            view.state().board().removePiece(new Position(2, 4));
            return actions.get(0);
        };
        Match match = new Match(state(), new ClassicRuleSet(P1, P2), agent, first);
        match.start();
        match.playTurn();
        assertThat(match.state().aliveCount(P2)).isEqualTo(1);
    }
}
