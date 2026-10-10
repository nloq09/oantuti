package com.rpsbattle.websocket.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.websocket.message.ClientMessage;
import com.rpsbattle.websocket.message.ServerMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    protected final ObjectMapper json;

    public GameWebSocketHandler(ObjectMapper json) {
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    protected synchronized void handleTextMessage(WebSocketSession session, TextMessage text) throws IOException {
        try {
            ClientMessage message = json.readValue(text.getPayload(), ClientMessage.class);
            if (message == null || message.type() == null) throw new IllegalArgumentException();
            send(session, "ACTION_REJECTED", Map.of("error", "UNSUPPORTED_MESSAGE"));
        } catch (IllegalArgumentException | IOException failure) {
            send(session, "ACTION_REJECTED", Map.of("error", "INVALID_MESSAGE"));
        }
    }

    protected void send(WebSocketSession session, String type, Map<String, Object> payload) throws IOException {
        if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(new ServerMessage(type, payload))));
    }
}
