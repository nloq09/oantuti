package com.rpsbattle.game.engine;

/** A rejected action; the input state remains unchanged. */
public final class InvalidActionException extends IllegalArgumentException {
    public InvalidActionException(String message) {
        super(message);
    }
}
