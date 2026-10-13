package com.rpsbattle.bot;

import java.time.Duration;

/**
 * Transport seam: a future sandbox runner can implement this without changing BotAgent.
 */
public interface BotProcess extends AutoCloseable {
    void start();

    String exchange(String request, Duration timeout);

    boolean isAlive();
    default void bindMatch(String matchId) { /* Optional transport lifecycle context. */ }

    @Override
    void close();
}
