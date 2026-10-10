package com.rpsbattle.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.agent.*;
import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.engine.*;
import com.rpsbattle.game.piece.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.*;
import com.rpsbattle.match.Match;
import com.rpsbattle.rules.classic.*;
import com.rpsbattle.app.LocalGameApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReplayServiceTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    @TempDir Path directory;
    private final ReplayService service = new ReplayService();
    private final ReplayJson json = new ReplayJson();

    private Match seeded() {
        Match match = new Match(ClassicGameSetup.create("M1", P1, P2), new ClassicRuleSet(P1, P2, 50),
                new RandomBotAgent(42), new RandomBotAgent(43), 42L);
        match.runToCompletion();
        return match;
    }

    private void sameState(GameState expected, GameState actual) {
        // Structural equality compares every field, not Piece's id-only equality.
        assertThat(json.state(actual)).isEqualTo(json.state(expected));
    }

    @Test
    void exportedSeededMatchReplaysEveryFinalStateField() throws Exception {
        Match match = seeded();
        Path file = directory.resolve("match-M1.json");
        service.write(file, match);
        MatchLog loaded = service.read(file);
        assertThat(loaded.seed()).isEqualTo(42L);
        assertThat(loaded.ruleSet()).isEqualTo("Classic");
        sameState(match.initialState(), loaded.initialState());
        sameState(match.state(), service.replay(file));
        sameState(match.state(), service.replay(match.initialState(), match.events()));
        assertThat(match.initialState().phase()).isEqualTo(GamePhase.READY);
        var root = new ObjectMapper().readTree(file.toFile());
        assertThat(root.get("events").get(0).get("type").asText()).isEqualTo("MATCH_STARTED");
        assertThat(root.get("events").get(1).get("type").asText()).isEqualTo("TURN_STARTED");
        root.get("events").forEach(e -> assertThat(e.has("sequence") && e.has("type") && e.has("payload")).isTrue());
        assertThat(root.get("events").get(root.get("events").size() - 1).get("type").asText()).isEqualTo("MATCH_FINISHED");
    }

    @ParameterizedTest
    @EnumSource(PieceType.class)
    void replaysAllCombatOutcomesIncludingBothLastPiecesDying(PieceType defender) {
        Board board = Board.defaultBoard();
        board.placePiece(Piece.alive("a", P1, PieceType.ROCK, new Position(3, 3)));
        board.placePiece(Piece.alive("d", P2, defender, new Position(2, 3)));
        GameState initial = GameState.initial("combat", board, List.of(Player.of(P1, "A"), Player.of(P2, "B"))).withPhase(GamePhase.READY);
        Match match = new Match(initial, new ClassicRuleSet(P1, P2),
                (v, a) -> MoveAction.of(P1, "a", new Position(3, 3), new Position(2, 3)), (v, a) -> a.get(0));
        match.runToCompletion();
        Path file = directory.resolve("combat.json");
        service.write(file, match);
        sameState(match.state(), service.replay(file));
        assertThat(match.events()).anyMatch(e -> e.type().equals("COMBAT_OCCURRED"))
                .anyMatch(e -> e.type().equals("PIECE_REMOVED"));
    }

    @Test
    void passAndSurrenderReplayWithoutBoardChanges() {
        int[] turns = {0};
        PlayerAgent player = (view, actions) -> turns[0]++ == 0 ? PassAction.of(P1) : SurrenderAction.of(P1);
        Match match = new Match(ClassicGameSetup.create("M1", P1, P2), new ClassicRuleSet(P1, P2),
                player, (v, a) -> PassAction.of(P2));
        match.runToCompletion();
        sameState(match.state(), service.replay(match.initialState(), match.events()));
        assertThat(match.state().turnNumber()).isEqualTo(3);
    }

    @Test
    void alreadyDecidedInitialBoardHasReplayableLifecycle() {
        var initial = ClassicGameSetup.create("M1", P1, P2);
        Board board = initial.board();
        initial.alivePiecesOf(P2).forEach(p -> board.removePiece(p.position()));
        Match match = new Match(initial.withBoard(board), new ClassicRuleSet(P1, P2), (v,a) -> a.get(0), (v,a) -> a.get(0));
        match.runToCompletion();
        assertThat(match.events()).hasSize(2);
        sameState(match.state(), service.replay(match.initialState(), match.events()));
    }

    @Test
    void rejectedActionDoesNotAddEventsAndListIsImmutable() {
        Match match = new Match(ClassicGameSetup.create("M1", P1, P2), new ClassicRuleSet(P1, P2),
                (v,a) -> PassAction.of(P2), (v,a) -> a.get(0));
        match.start();
        var before = match.events();
        assertThatThrownBy(match::playTurn).isInstanceOf(InvalidActionException.class);
        assertThat(match.events()).isEqualTo(before);
        assertThatThrownBy(() -> match.events().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesIncompleteOutOfOrderOrRepeatedLifecycle() {
        Match match = seeded();
        var events = new ArrayList<>(match.events());
        events.remove(events.size() - 1);
        assertThatThrownBy(() -> service.replay(match.initialState(), events)).isInstanceOf(IllegalArgumentException.class);
        events.clear(); events.addAll(match.events());
        events.set(1, new GameEvent.TurnStarted(match.initialState().sequence() + 3, P1));
        assertThatThrownBy(() -> service.replay(match.initialState(), events)).isInstanceOf(IllegalArgumentException.class);
        events.clear(); events.addAll(match.events()); events.add(events.get(0));
        assertThatThrownBy(() -> service.replay(match.initialState(), events)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedStoredResult() {
        Match match = seeded();
        GameResult wrong = match.state().result().orElseThrow().equals(GameResult.draw()) ? GameResult.win(P1) : GameResult.draw();
        MatchLog log = new MatchLog(match.matchId(), "Classic", 42L, match.initialState(), match.events(), wrong);
        assertThatThrownBy(() -> service.replay(log)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("result");
    }

    @Test
    void cliExportsAndReadsHumanMatch() {
        Path file = directory.resolve("human.json");
        StringWriter output = new StringWriter();
        assertThat(LocalGameApplication.execute(new String[]{"human-vs-human", "--export", file.toString()},
                new StringReader("surrender\n"), output)).isZero();
        assertThat(LocalGameApplication.execute(new String[]{"replay", file.toString()}, new StringReader(""), output)).isZero();
        assertThat(output.toString()).contains("Replay result: WIN(P2)");
        assertThat(service.read(file).seed()).isNull();
    }

    @Test
    void refusesExportBeforeFinish() {
        Match match = new Match(ClassicGameSetup.create("M1", P1, P2), new ClassicRuleSet(P1, P2), (v,a)->a.get(0), (v,a)->a.get(0));
        assertThatThrownBy(() -> service.write(directory.resolve("not-finished.json"), match)).isInstanceOf(IllegalStateException.class);
    }
}
