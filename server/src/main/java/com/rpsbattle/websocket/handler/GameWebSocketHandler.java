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
    private final Map<PlayerId, WebSocketSession> seats = new HashMap<>();
    private final Map<String, PlayerId> players = new HashMap<>();

    public GameWebSocketHandler(ObjectMapper json, DemoMatchService service, GameStateMapper mapper) {
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    protected synchronized void handleTextMessage(WebSocketSession session, TextMessage text) throws IOException {
        try {
            ClientMessage message = json.readValue(text.getPayload(), ClientMessage.class);
            if (message == null || message.type() == null) throw new IllegalArgumentException();
            if (!DemoMatchService.MATCH_ID.equals(message.matchId())) throw new DemoRequestException("MATCH_NOT_FOUND");
            PlayerId player = PlayerId.of(message.playerId());
            if (!service.hasPlayer(player)) throw new DemoRequestException("PLAYER_NOT_FOUND");
            switch (message.type()) {
                case "JOIN" -> join(session, player);
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
        sendState(session, player, List.of());
    }

    private void sendState(WebSocketSession session, PlayerId player, List<GameEvent> events) {
        var state = mapper.toDto(service.state(), service.legalActions(player), events);
        send(session, "STATE", Map.of("gameState", state));
    }

    private void reject(WebSocketSession session, String error) {
        send(session, "ACTION_REJECTED", Map.of("error", error));
    }

    private void send(WebSocketSession session, String type, Map<String, Object> payload) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(new ServerMessage(type, payload))));
        } catch (IOException failure) {
            release(session);
            try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { /* Already disconnected. */ }
        }
    }

    private void release(WebSocketSession session) {
        PlayerId player = players.remove(session.getId());
        if (player != null) seats.remove(player, session);
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) { release(session); }

    @Override
    public synchronized void handleTransportError(WebSocketSession session, Throwable error) throws IOException {
        release(session);
        session.close(CloseStatus.SERVER_ERROR);
    }
}
