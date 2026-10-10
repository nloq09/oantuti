package com.rpsbattle.websocket.message;

import com.fasterxml.jackson.databind.JsonNode;

public record OnlineMessage(String type, String matchId, String playerId, String token, Long sequence,
                            JsonNode payload) {
}
