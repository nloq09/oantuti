package com.rpsbattle.websocket.message;

import com.rpsbattle.api.dto.ActionDto;

public record ClientMessage(String type, String matchId, String playerId, Long sequence, ActionDto payload) {
}
