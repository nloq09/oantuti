package com.rpsbattle.replay;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.piece.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.*;
import com.rpsbattle.rule.CombatResult;

import java.util.*;

/**
 * Explicit JSON schema keeps Jackson annotations out of all domain classes.
 */
final class ReplayJson {
    final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    JsonNode state(GameState s) {
        var b = s.board();
        return mapper.valueToTree(new StateData(s.matchId(), b.rows(), b.cols(),
                b.allPieces().stream().sorted(Comparator.comparing(Piece::id)).map(PieceData::new).toList(),
                s.players().stream().map(p -> new PlayerData(p.id().value(), p.name(), p.pieceIds())).toList(),
                s.currentPlayerId().value(), s.phase(), s.turnNumber(), s.sequence(), s.result().map(ResultData::new).orElse(null)));
    }

    GameState state(JsonNode json) {
        if (json == null || !json.isObject()) throw new IllegalArgumentException("Missing initialState object");
        StateData s = mapper.convertValue(json, StateData.class);
        if (s.matchId() == null || s.pieces() == null || s.players() == null || s.currentPlayer() == null || s.phase() == null) {
            throw new IllegalArgumentException("Missing snapshot fields");
        }
        if (s.turnNumber() < 0 || s.sequence() < 0) throw new IllegalArgumentException("Invalid snapshot counters");
        Board board = new Board(s.rows(), s.cols());
        Set<String> ids = new HashSet<>();
        for (PieceData p : s.pieces()) {
            if (!ids.add(p.id()) || !board.isEmpty(p.position()))
                throw new IllegalArgumentException("Duplicate piece or cell");
            board.placePiece(p.piece());
        }
        var players = s.players().stream().map(p -> new Player(PlayerId.of(p.id()), p.name(), p.pieceIds())).toList();
        if (players.stream().map(Player::id).distinct().count() != players.size()
                || players.stream().noneMatch(p -> p.id().value().equals(s.currentPlayer()))) {
            throw new IllegalArgumentException("Invalid snapshot players");
        }
        for (Piece p : board.allPieces()) {
            if (players.stream().noneMatch(player -> player.id().equals(p.ownerId())))
                throw new IllegalArgumentException("Unknown piece owner");
        }
        GameState result = GameState.initial(s.matchId(), board, players).withCurrentPlayer(PlayerId.of(s.currentPlayer()))
                .withPhase(s.phase()).withTurnNumber(s.turnNumber());
        // Public GameState withers keep domain serialization-independent.
        result = result.withSequence(s.sequence());
        return s.result() == null ? result : result.withResult(s.result().result());
    }

    JsonNode result(GameResult result) {
        return mapper.valueToTree(new ResultData(result));
    }

    GameResult result(JsonNode json) {
        if (json == null || !json.isObject()) throw new IllegalArgumentException("Missing result object");
        return mapper.convertValue(json, ResultData.class).result();
    }

    private Piece piece(JsonNode json) {
        return mapper.convertValue(json, PieceData.class).piece();
    }

    private Position position(JsonNode json) {
        return mapper.convertValue(json, Position.class);
    }

    private PlayerId player(JsonNode json, String name) {
        return PlayerId.of(text(json, name));
    }

    private String text(JsonNode node, String name) {
        JsonNode v = node.get(name);
        if (v == null || !v.isTextual()) throw new IllegalArgumentException("Missing " + name);
        return v.textValue();
    }

    private long number(JsonNode node, String name) {
        JsonNode v = node.get(name);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong())
            throw new IllegalArgumentException("Invalid " + name);
        return v.longValue();
    }

    JsonNode event(GameEvent e) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sequence", e.sequence());
        node.put("type", e.type());
        ObjectNode p = node.putObject("payload");
        switch (e) {
            case GameEvent.MatchStarted ignored -> {
            }
            case GameEvent.TurnStarted v -> p.put("player", v.player().value());
            case GameEvent.PiecePlaced v -> p.set("piece", mapper.valueToTree(new PieceData(v.piece())));
            case GameEvent.PieceMoved v -> {
                p.put("pieceId", v.pieceId());
                p.set("from", mapper.valueToTree(v.from()));
                p.set("to", mapper.valueToTree(v.to()));
            }
            case GameEvent.CombatOccurred v -> {
                p.set("attacker", mapper.valueToTree(new PieceData(v.attacker())));
                p.set("defender", mapper.valueToTree(new PieceData(v.defender())));
                p.put("result", v.result().name());
            }
            case GameEvent.PieceRemoved v -> p.set("piece", mapper.valueToTree(new PieceData(v.piece())));
            case GameEvent.TurnChanged v -> {
                p.put("previousPlayer", v.previousPlayer().value());
                p.put("currentPlayer", v.currentPlayer().value());
                p.put("turnNumber", v.turnNumber());
            }
            case GameEvent.TurnEnded v -> {
                p.put("currentPlayer", v.currentPlayer().value());
                p.put("turnNumber", v.turnNumber());
            }
            case GameEvent.GameFinished v -> {
                p.set("result", result(v.result()));
                p.put("turnNumber", v.turnNumber());
            }
            case GameEvent.MatchFinished v -> p.set("result", result(v.result()));
        }
        return node;
    }

    GameEvent event(JsonNode node) {
        long sequence = number(node, "sequence");
        JsonNode p = node.get("payload");
        if (p == null || !p.isObject()) throw new IllegalArgumentException("Missing event payload");
        return switch (text(node, "type")) {
            case "MATCH_STARTED" -> new GameEvent.MatchStarted(sequence);
            case "TURN_STARTED" -> new GameEvent.TurnStarted(sequence, player(p, "player"));
            case "PIECE_PLACED" -> new GameEvent.PiecePlaced(sequence, piece(p.get("piece")));
            case "PIECE_MOVED" ->
                    new GameEvent.PieceMoved(sequence, text(p, "pieceId"), position(p.get("from")), position(p.get("to")));
            case "COMBAT_OCCURRED" ->
                    new GameEvent.CombatOccurred(sequence, piece(p.get("attacker")), piece(p.get("defender")), CombatResult.valueOf(text(p, "result")));
            case "PIECE_REMOVED" -> new GameEvent.PieceRemoved(sequence, piece(p.get("piece")));
            case "TURN_CHANGED" ->
                    new GameEvent.TurnChanged(sequence, player(p, "previousPlayer"), player(p, "currentPlayer"), Math.toIntExact(number(p, "turnNumber")));
            case "TURN_ENDED" ->
                    new GameEvent.TurnEnded(sequence, player(p, "currentPlayer"), Math.toIntExact(number(p, "turnNumber")));
            case "GAME_FINISHED" ->
                    new GameEvent.GameFinished(sequence, result(p.get("result")), Math.toIntExact(number(p, "turnNumber")));
            case "MATCH_FINISHED" -> new GameEvent.MatchFinished(sequence, result(p.get("result")));
            default -> throw new IllegalArgumentException("Unknown event type");
        };
    }

    private record PieceData(String id, String ownerId, PieceType type, Position position, PieceStatus status) {
        PieceData(Piece p) {
            this(p.id(), p.ownerId().value(), p.type(), p.position(), p.status());
        }

        Piece piece() {
            return new Piece(id, PlayerId.of(ownerId), type, position, status);
        }
    }

    private record PlayerData(String id, String name, List<String> pieceIds) {
    }

    private record ResultData(GameOutcome outcome, String winner) {
        ResultData(GameResult r) {
            this(r.outcome(), r.winner().map(PlayerId::value).orElse(null));
        }

        GameResult result() {
            if (outcome == GameOutcome.DRAW && winner == null) return GameResult.draw();
            if (outcome == GameOutcome.WIN && winner != null) return GameResult.win(PlayerId.of(winner));
            throw new IllegalArgumentException("Invalid result");
        }
    }

    private record StateData(String matchId, int rows, int cols, List<PieceData> pieces,
                             List<PlayerData> players, String currentPlayer, GamePhase phase,
                             int turnNumber, long sequence, ResultData result) {
    }
}
