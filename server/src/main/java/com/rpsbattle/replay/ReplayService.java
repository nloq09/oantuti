package com.rpsbattle.replay;

import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.state.*;
import com.rpsbattle.match.Match;
import com.rpsbattle.match.MatchStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies recorded facts, without agents, randomness or re-running combat rules.
 */
public final class ReplayService {
    private final ReplayJson json = new ReplayJson();

    private static void invalid(String message) {
        throw new IllegalArgumentException(message);
    }

    public void write(Path path, Match match) {
        if (match.status() != MatchStatus.FINISHED) throw new IllegalStateException("Export requires a finished match");
        MatchLog log = new MatchLog(match.matchId(), match.ruleSet().name(), match.seed(), match.initialState(),
                match.events(), match.state().result().orElseThrow());
        var node = json.mapper.createObjectNode();
        node.put("matchId", log.matchId());
        node.put("ruleSet", log.ruleSet());
        if (log.seed() == null) node.putNull("seed");
        else node.put("seed", log.seed());
        node.set("initialState", json.state(log.initialState()));
        var events = node.putArray("events");
        log.events().forEach(e -> events.add(json.event(e)));
        node.set("result", json.result(log.result()));
        try {
            json.mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), node);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot export replay: " + path, e);
        }
    }

    public MatchLog read(Path path) {
        try {
            var node = json.mapper.readTree(path.toFile());
            if (node == null || !node.isObject() || !node.path("matchId").isTextual()
                    || !node.path("ruleSet").isTextual() || !node.path("events").isArray() || !node.has("seed")) {
                throw new IllegalArgumentException("Invalid match log schema");
            }
            var seed = node.get("seed");
            if (!seed.isNull() && (!seed.isIntegralNumber() || !seed.canConvertToLong()))
                throw new IllegalArgumentException("Invalid seed");
            List<GameEvent> events = new ArrayList<>();
            node.get("events").forEach(e -> events.add(json.event(e)));
            return new MatchLog(node.get("matchId").asText(), node.get("ruleSet").asText(),
                    seed.isNull() ? null : seed.longValue(), json.state(node.get("initialState")), events, json.result(node.get("result")));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read replay: " + path, e);
        } catch (NullPointerException | java.util.NoSuchElementException e) {
            throw new IllegalArgumentException("Missing replay payload fields", e);
        }
    }

    public GameState replay(Path path) {
        return replay(read(path));
    }

    public GameState replay(MatchLog log) {
        GameState state = replay(log.initialState(), log.events());
        if (!state.result().orElseThrow().equals(log.result()))
            throw new IllegalArgumentException("Replay result does not match log result");
        return state;
    }

    public GameState replay(GameState initialState, List<GameEvent> events) {
        GameState state = initialState;
        boolean started = false, active = false, finished = false, hadTurn = false;
        long pending = state.sequence();
        for (GameEvent event : events) {
            if (finished) invalid("Events after MATCH_FINISHED");
            if (event instanceof GameEvent.MatchStarted) {
                if (started || event.sequence() != state.sequence()) invalid("Invalid MATCH_STARTED");
                started = true;
                state = state.withPhase(GamePhase.PLAYING);
                continue;
            }
            if (!started) invalid("Missing MATCH_STARTED");
            if (event instanceof GameEvent.TurnStarted turn) {
                if (active || state.isFinished() || turn.sequence() != Math.addExact(state.sequence(), 1)
                        || !turn.player().equals(state.currentPlayerId())) invalid("Invalid TURN_STARTED");
                active = true;
                hadTurn = true;
                pending = turn.sequence();
                continue;
            }
            if (event instanceof GameEvent.MatchFinished end) {
                if (active || end.sequence() != state.sequence()) invalid("Invalid MATCH_FINISHED");
                if (hadTurn && state.result().isEmpty()) invalid("Missing GAME_FINISHED");
                if (state.result().isPresent() && !state.result().get().equals(end.result()))
                    invalid("Conflicting final result");
                state = state.withResult(end.result()).withPhase(GamePhase.FINISHED);
                finished = true;
                continue;
            }
            if (!active || event.sequence() != pending) invalid("Event outside its turn or sequence");
            if (state.isFinished() && !(event instanceof GameEvent.TurnEnded)) invalid("Event after GAME_FINISHED");
            var board = state.board();
            switch (event) {
                case GameEvent.PieceMoved e -> {
                    var piece = board.getPiece(e.from()).orElseThrow(() -> new IllegalArgumentException("Missing move source"));
                    if (!piece.id().equals(e.pieceId())) invalid("Move piece mismatch");
                    board.movePiece(e.from(), e.to());
                    state = state.withBoard(board);
                }
                case GameEvent.PieceRemoved e -> {
                    checkPiece(board.getPiece(e.piece().position()).orElseThrow(), e.piece());
                    board.removePiece(e.piece().position());
                    state = state.withBoard(board);
                }
                case GameEvent.PiecePlaced e -> {
                    if (board.findById(e.piece().id()).isPresent()) invalid("Duplicate placed piece");
                    board.placePiece(e.piece());
                    state = state.withBoard(board);
                    var player = state.findPlayer(e.piece().ownerId()).orElseThrow();
                    state = state.withUpdatedPlayer(player.withAddedPiece(e.piece().id()));
                }
                case GameEvent.CombatOccurred e -> {
                    checkPiece(board.getPiece(e.attacker().position()).orElseThrow(), e.attacker());
                    checkPiece(board.getPiece(e.defender().position()).orElseThrow(), e.defender());
                }
                case GameEvent.TurnChanged e -> {
                    if (!state.currentPlayerId().equals(e.previousPlayer()) || state.findPlayer(e.currentPlayer()).isEmpty()
                            || e.turnNumber() < state.turnNumber()) invalid("Invalid turn change");
                    state = state.withCurrentPlayer(e.currentPlayer()).withTurnNumber(e.turnNumber());
                }
                case GameEvent.GameFinished e -> {
                    if (e.turnNumber() < state.turnNumber()) invalid("Invalid final turn");
                    state = state.withResult(e.result()).withPhase(GamePhase.FINISHED).withTurnNumber(e.turnNumber());
                }
                case GameEvent.TurnEnded e -> {
                    if (!state.currentPlayerId().equals(e.currentPlayer()) || e.turnNumber() != state.turnNumber())
                        invalid("Invalid TURN_ENDED");
                    state = state.withSequence(e.sequence());
                    active = false;
                }
                default -> invalid("Unexpected lifecycle event");
            }
        }
        if (!finished || active) invalid("Incomplete match log");
        return state;
    }

    private void checkPiece(Piece actual, Piece expected) {
        if (!actual.id().equals(expected.id()) || !actual.ownerId().equals(expected.ownerId())
                || actual.type() != expected.type() || !actual.position().equals(expected.position())
                || actual.status() != expected.status()) invalid("Piece payload mismatch");
    }
}
