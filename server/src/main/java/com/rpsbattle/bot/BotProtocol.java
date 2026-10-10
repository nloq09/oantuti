package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * JSON adaptation stays outside game core; explicit schema avoids domain annotations.
 */
public final class BotProtocol {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public String encodeTurn(PlayerView view, List<Action> actions) {
        var game = view.state();
        var board = game.board();
        ObjectNode state = mapper.createObjectNode();
        state.put("phase", game.phase().name());
        state.put("currentPlayer", game.currentPlayerId().value());
        ObjectNode boardJson = state.putObject("board");
        boardJson.put("rows", board.rows());
        boardJson.put("cols", board.cols());
        var pieces = boardJson.putArray("pieces");
        board.allPieces().stream().sorted(Comparator.comparing(Piece::id)).forEach(piece -> {
            ObjectNode json = pieces.addObject();
            json.put("id", piece.id());
            json.put("ownerId", piece.ownerId().value());
            json.put("type", piece.type().name());
            json.put("status", piece.status().name());
            json.set("position", mapper.valueToTree(piece.position()));
        });
        var players = state.putArray("players");
        game.players().forEach(player -> {
            ObjectNode json = players.addObject();
            json.put("id", player.id().value());
            json.put("name", player.name());
            json.set("pieceIds", mapper.valueToTree(player.pieceIds()));
        });
        BotTurnRequest request = new BotTurnRequest("YOUR_TURN", game.matchId(), game.turnNumber(),
                game.sequence(), view.playerId().value(), state, actions.stream().map(this::encodeAction).toList());
        try {
            return mapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new BotProtocolException("Cannot encode turn request", e);
        }
    }

    private JsonNode encodeAction(Action action) {
        ObjectNode json = mapper.createObjectNode();
        switch (action) {
            case MoveAction move -> {
                json.put("type", "MOVE");
                json.put("pieceId", move.pieceId());
                json.set("from", mapper.valueToTree(move.from()));
                json.set("to", mapper.valueToTree(move.to()));
            }
            case PassAction ignored -> json.put("type", "PASS");
            case SurrenderAction ignored -> json.put("type", "SURRENDER");
            case PlaceAction ignored ->
                    throw new BotProtocolException("Bot protocol currently supports playing actions only");
        }
        return json;
    }

    public Action decodeResponse(String line, long expectedSequence, PlayerId player) {
        try {
            JsonNode json = mapper.readTree(line);
            if (json == null || !json.isObject()) throw new BotProtocolException("Expected ACTION object");
            if (!text(json, "type").equals("ACTION")) throw new BotProtocolException("Expected ACTION message type");
            JsonNode sequence = json.get("sequence");
            if (sequence == null || !sequence.isIntegralNumber() || !sequence.canConvertToLong()) {
                throw new BotProtocolException("Missing or invalid sequence");
            }
            if (sequence.longValue() != expectedSequence)
                throw new BotProtocolException("Stale or mismatched sequence");
            BotActionResponse response = new BotActionResponse("ACTION", sequence.longValue(), json.get("action"));
            JsonNode action = response.action();
            if (action == null || !action.isObject()) throw new BotProtocolException("Missing action object");
            if (action.has("playerId") && !text(action, "playerId").equals(player.value())) {
                throw new BotProtocolException("Action playerId does not match assigned player");
            }
            return switch (text(action, "type")) {
                case "MOVE" ->
                        MoveAction.of(player, text(action, "pieceId"), position(action.get("from")), position(action.get("to")));
                case "PASS" -> PassAction.of(player);
                case "SURRENDER" -> SurrenderAction.of(player);
                default -> throw new BotProtocolException("Unknown action type");
            };
        } catch (IOException | IllegalArgumentException e) {
            throw new BotProtocolException("Invalid bot response: " + e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new BotProtocolException("Missing or invalid " + field);
        }
        return value.textValue();
    }

    private Position position(JsonNode node) {
        if (node == null || !node.isObject()) throw new BotProtocolException("Missing position object");
        return new Position(integer(node, "row"), integer(node, "col"));
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new BotProtocolException("Missing or invalid " + field);
        }
        return value.intValue();
    }
}
