package com.rpsbattle.api.dto;

import java.util.Map;

public record GameEventDto(long sequence, String type, Map<String, Object> payload) {
    public GameEventDto {
        payload = Map.copyOf(payload);
    }
}
