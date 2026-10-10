package com.rpsbattle.websocket.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.websocket.message.ClientMessage;
import com.rpsbattle.websocket.message.ServerMessage;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.online.DemoMatchService;
import com.rpsbattle.online.DemoRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final DemoMatchService service;
    private final GameStateMapper mapper;
    private final OnlineGameGateway online;
    private final Map<PlayerId, WebSocketSession> seats = new HashMap<>();
    private final Map<String, PlayerId> players = new HashMap<>();

    public GameWebSocketHandler(ObjectMapper json, DemoMatchService service, GameStateMapper mapper, OnlineGameGateway online) {
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.service = service;
        this.mapper = mapper;
        this.online = online;
    }

    @Override
    protected synchronized void handleTextMessage(WebSocketSession session, TextMessage text) throws IOException {
        try {
            var envelope = json.readTree(text.getPayload());
            if (envelope != null && (!envelope.path("matchId").asText().equals(DemoMatchService.MATCH_ID))) {
                online.handle(session, text.getPayload());
                return;
            }
            ClientMessage message = json.readValue(text.getPayload(), ClientMessage.class);
            if (message == null || message.type() == null) throw new IllegalArgumentException();
            if (!DemoMatchService.MATCH_ID.equals(message.matchId())) throw new DemoRequestException("MATCH_NOT_FOUND");
            PlayerId player = PlayerId.of(message.playerId());
            if (!service.hasPlayer(player)) throw new DemoRequestException("PLAYER_NOT_FOUND");
            switch (message.type()) {
                case "JOIN" -> join(session, player);
                case "ACTION" -> action(session, player, message);
                default -> throw new DemoRequestException("UNSUPPORTED_MESSAGE");
            }
        } catch (DemoRequestException failure) {
            reject(session, failure.code());
        } catch (IllegalArgumentException | NullPointerException | IOException failure) {
            reject(session, "INVALID_MESSAGE");
        }
    }

    private void join(WebSocketSession session, PlayerId player) {
        PlayerId previous = players.get(session.getId());
        if (previous != null && !previous.equals(player)) throw new DemoRequestException("ALREADY_JOINED");
        WebSocketSession existing = seats.get(player);
        if (existing != null && !existing.getId().equals(session.getId())) {
            if (existing.isOpen()) throw new DemoRequestException("SEAT_TAKEN");
            release(existing);
        }
        seats.put(player, session);
        players.put(session.getId(), player);
        if (seats.size() == 2) broadcast(List.of());
        else sendState(session, player, List.of());
    }

    private void action(WebSocketSession session, PlayerId player, ClientMessage message) {
        PlayerId bound = players.get(session.getId());
        if (bound == null) throw new DemoRequestException("JOIN_REQUIRED");
        if (!bound.equals(player)) throw new DemoRequestException("PLAYER_MISMATCH");
        if (seats.size() != 2) throw new DemoRequestException("WAITING_FOR_PLAYER");
        if (message.sequence() == null || message.payload() == null) throw new DemoRequestException("INVALID_MESSAGE");
        try {
            var transition = service.submitAction(player, message.sequence(), message.payload().toDomain(player));
            broadcast(transition.events());
        } catch (DemoRequestException failure) {
            if (failure.code().equals("INVALID_SEQUENCE")) {
                reject(session, failure.code());
                sendState(session, player, List.of());
            } else throw failure;
        }
    }

    private void broadcast(List<GameEvent> events) {
        for (var seat : List.copyOf(seats.entrySet())) sendState(seat.getValue(), seat.getKey(), events);
    }

    private void sendState(WebSocketSession session, PlayerId player, List<GameEvent> events) {
        var state = mapper.toDto(service.state(), service.legalActions(player), events);
        send(session, "STATE", Map.of("gameState", state));
        if (state.phase().equals("FINISHED")) {
            send(session, "GAME_OVER", Map.of("winner", state.winner() == null ? "DRAW" : state.winner(),
                    "outcome", state.outcome(), "sequence", state.sequence()));
        }
    }

    private void reject(WebSocketSession session, String error) {
        send(session, "ACTION_REJECTED", Map.of("error", error));
    }

    private void send(WebSocketSession session, String type, Map<String, Object> payload) {
        try {
            if (session.isOpen())
                session.sendMessage(new TextMessage(json.writeValueAsString(new ServerMessage(type, payload))));
        } catch (IOException failure) {
            release(session);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) { /* Already disconnected. */ }
        }
    }

    private void release(WebSocketSession session) {
        PlayerId player = players.remove(session.getId());
        if (player != null) seats.remove(player, session);
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        release(session);
        online.closed(session);
    }

    @Override
    public synchronized void handleTransportError(WebSocketSession session, Throwable error) throws IOException {
        release(session);
        online.closed(session);
        session.close(CloseStatus.SERVER_ERROR);
    }
}
