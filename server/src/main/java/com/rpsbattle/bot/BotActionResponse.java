package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.JsonNode;

public record BotActionResponse(String type, long sequence, JsonNode action) implements BotProtocolMessage {
}
