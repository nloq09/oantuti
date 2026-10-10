package com.rpsbattle.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OnlineSnapshot(GameStateDto gameState,String mode,String status,String playerId,
                             List<PlayerSeatDto> players,Map<String,Integer> remaining,
                             List<PositionDto> placementCells,Instant deadline,Instant serverTime,
                             Instant startedAt,Instant finishedAt,String botError) {
    public record PlayerSeatDto(String playerId,boolean joined,boolean connected,boolean ready,boolean bot,String botName) {}
}
