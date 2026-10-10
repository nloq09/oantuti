package com.rpsbattle.game.player;

import java.util.Objects;

/**
 * Strongly-typed identifier for a player.
 * Wraps a String to avoid primitive obsession.
 */
public record PlayerId(String value) {

    public PlayerId {
        Objects.requireNonNull(value, "PlayerId value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("PlayerId value must not be blank");
    }

    /**
     * Convenience factory.
     */
    public static PlayerId of(String value) {
        return new PlayerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
