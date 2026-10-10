package com.rpsbattle.websocket.handler;

import com.fasterxml.jackson.databind.*;
import com.rpsbattle.api.dto.*;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.online.*;
import com.rpsbattle.websocket.message.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineGameGateway {
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();
    private final Map<String, Map<PlayerId, Binding>> seats = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final OnlineMatchService service;
    public OnlineGameGateway(ObjectMapper json, OnlineMatchService service) {
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.service = service;
        service.listen(this::broadcast);
    }

    private static void fail(String code) {
        throw new DemoRequestException(code);
    }

    public void handle(WebSocketSession socket, String text) {
        try {
            var message = json.readValue(text, OnlineMessage.class);
            if (message == null || message.type() == null) throw new IllegalArgumentException();
            if (message.type().equals("PING")) {
                send(socket, "PONG", Map.of());
                return;
            }
            var room = service.find(message.matchId());
            var player = PlayerId.of(message.playerId());
            synchronized (room) {
                if (message.type().equals("JOIN_MATCH") || message.type().equals("RESUME_MATCH")) {
                    room.authorize(player, message.token());
                    var prior = bindings.get(socket.getId());
                    if (prior != null && (!prior.room().id().equals(room.id()) || !prior.player().equals(player)))
                        fail("ALREADY_JOINED");
                    var players = seats.computeIfAbsent(room.id(), ignored -> new ConcurrentHashMap<>());
                    var existing = players.get(player);
                    if (existing != null && !existing.session().getId().equals(socket.getId())) {
                        if (existing.session().isOpen() && !message.type().equals("RESUME_MATCH")) fail("SEAT_TAKEN");
                        bindings.remove(existing.session().getId());
                        try {
                            existing.session().close(CloseStatus.NORMAL);
                        } catch (IOException ignored) {
                        }
                    }
                    var binding = new Binding(room, player, new ConcurrentWebSocketSessionDecorator(socket, 5000, 131072));
                    bindings.put(socket.getId(), binding);
                    players.put(player, binding);
                    room.connection(player, true);
                    var history = room.events();
                    service.publish(room, history.subList(Math.max(0, history.size() - 80), history.size()));
                    return;
                }
                var binding = bindings.get(socket.getId());
                if (binding == null) fail("JOIN_REQUIRED");
                if (binding.room() != room || !binding.player().equals(player)) fail("PLAYER_MISMATCH");
                if (room.uploadedBot(player) && Set.of("PLACE", "RANDOM_PLACEMENT", "RESET_PLACEMENT", "READY", "UNREADY").contains(message.type()))
                    fail("BOT_CONTROLLED");
                var payload = message.payload();
                List<com.rpsbattle.game.engine.GameEvent> events = List.of();
                switch (message.type()) {
                    case "ACTION" -> {
                        if (message.sequence() == null || payload == null) fail("INVALID_MESSAGE");
                        var elapsed = room.tick();
                        if (!elapsed.isEmpty()) service.publish(room, elapsed);
                        var action = json.treeToValue(payload, ActionDto.class).toDomain(player);
                        events = room.submit(player, message.sequence(), action);
                        send(binding.session(), "ACTION_ACCEPTED", Map.of("sequence", room.state().sequence()));
                    }
                    case "PLACE" -> {
                        if (payload == null || !payload.path("type").isTextual() || !payload.has("position"))
                            fail("INVALID_MESSAGE");
                        var position = json.treeToValue(payload.get("position"), PositionDto.class);
                        room.place(player, PieceType.valueOf(payload.path("type").asText()), position.toDomain(),
                                payload.hasNonNull("pieceId") ? payload.path("pieceId").asText() : null);
                    }
                    case "RANDOM_PLACEMENT" -> room.randomize(player);
                    case "RESET_PLACEMENT" -> room.reset(player);
                    case "READY" -> events = room.markReady(player);
                    case "UNREADY" -> room.unready(player);
                    case "LEAVE" -> events = room.leave(player);
                    case "STATE_REQUEST" -> {
                        state(binding, List.of());
                        return;
                    }
                    default -> fail("UNSUPPORTED_MESSAGE");
                }
                service.publish(room, events);
                if (message.type().equals("LEAVE")) {
                    send(binding.session(), "LEFT_ROOM", Map.of());
                    closed(socket);
                }
            }
        } catch (DemoRequestException error) {
            reject(socket, error.code());
            if (error.code().equals("INVALID_SEQUENCE")) {
                var binding = bindings.get(socket.getId());
                if (binding != null) state(binding, List.of());
            }
        } catch (IOException | IllegalArgumentException | NullPointerException error) {
            reject(socket, "INVALID_MESSAGE");
        }
    }

    public void closed(WebSocketSession socket) {
        var binding = bindings.remove(socket.getId());
        if (binding == null) return;
        synchronized (binding.room()) {
            var players = seats.get(binding.room().id());
            if (players != null && players.remove(binding.player(), binding)) {
                binding.room().connection(binding.player(), false);
                service.publish(binding.room(), List.of());
                if (players.isEmpty()) seats.remove(binding.room().id(), players);
            }
        }
    }

    private void broadcast(OnlineMatchService.Update update) {
        var players = seats.get(update.match().id());
        if (players == null) return;
        for (var binding : List.copyOf(players.values())) state(binding, update.events());
    }

    private void state(Binding binding, List<com.rpsbattle.game.engine.GameEvent> events) {
        var snapshot = service.snapshot(binding.room(), binding.player(), events);
        send(binding.session(), "GAME_STATE", Map.of("snapshot", snapshot));
        if (!events.isEmpty()) send(binding.session(), "GAME_EVENT", Map.of("events", snapshot.gameState().events()));
        if (snapshot.status().equals("PLAYING") && snapshot.gameState().currentPlayer().equals(binding.player().value()))
            send(binding.session(), "YOUR_TURN", Map.of("sequence", snapshot.gameState().sequence()));
        if (snapshot.status().equals("FINISHED")) send(binding.session(), "GAME_OVER", Map.of("snapshot", snapshot));
    }

    private void reject(WebSocketSession socket, String code) {
        send(socket, "ACTION_REJECTED", Map.of("error", code));
    }

    private void send(WebSocketSession socket, String type, Map<String, Object> payload) {
        try {
            if (socket.isOpen())
                socket.sendMessage(new TextMessage(json.writeValueAsString(new ServerMessage(type, payload))));
        } catch (IOException | RuntimeException error) {
            closed(socket);
            try {
                socket.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
            }
        }
    }

    private record Binding(OnlineMatch room, PlayerId player, WebSocketSession session) {
    }
}
