package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record BotTurnRequest(String type, String matchId, int turn, long sequence,
                             String player, JsonNode state, List<JsonNode> legalActions)
        implements BotProtocolMessage {
    public BotTurnRequest {
        legalActions = List.copyOf(legalActions);
    }
}
