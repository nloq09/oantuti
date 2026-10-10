package com.rpsbattle.bot;

public sealed interface BotProtocolMessage permits BotTurnRequest, BotActionResponse {
    String type();

    long sequence();
}
