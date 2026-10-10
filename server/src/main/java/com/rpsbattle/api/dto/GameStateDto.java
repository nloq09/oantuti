package com.rpsbattle.api.dto;

import java.util.List;

public record GameStateDto(String matchId, int boardWidth, int boardHeight, List<PieceDto> pieces,
                           String currentPlayer, int turn, long sequence, String phase,
                           String winner, String outcome, List<ActionDto> legalActions,
                           List<GameEventDto> events) {
    public GameStateDto {
        pieces = List.copyOf(pieces);
        legalActions = List.copyOf(legalActions);
        events = List.copyOf(events);
    }
}
