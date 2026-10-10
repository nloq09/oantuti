package com.rpsbattle.bot;

public final class BotProtocolException extends IllegalStateException {
    public BotProtocolException(String message) {
        super(message);
    }

    public BotProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
