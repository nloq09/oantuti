package com.rpsbattle.websocket.message;

import java.util.Map;

public record ServerMessage(String type, Map<String, Object> payload) {}
