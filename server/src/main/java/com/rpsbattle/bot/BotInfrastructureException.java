package com.rpsbattle.bot;
/** Runner/daemon failure is not a player's invalid action or bot crash. */
public final class BotInfrastructureException extends RuntimeException {
    public BotInfrastructureException(){super("BOT_INFRASTRUCTURE_UNAVAILABLE");}
}
