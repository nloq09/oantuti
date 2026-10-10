package com.rpsbattle.bot;

import com.rpsbattle.agent.PlayerAgent;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.action.Action;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Does not call GameEngine: the owning Match validates the returned action.
 */
public final class BotAgent implements PlayerAgent, AutoCloseable {
    private final BotProcess process;
    private final Duration timeout;
    private final BotProtocol protocol = new BotProtocol();

    public BotAgent(BotProcess process, Duration timeout) {
        this.process = Objects.requireNonNull(process, "process");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
    }

    @Override
    public Action chooseAction(PlayerView view, List<Action> legalActions) {
        String request = protocol.encodeTurn(view, legalActions);
        try {
            return protocol.decodeResponse(process.exchange(request, timeout), view.state().sequence(), view.playerId());
        } catch (BotProtocolException e) {
            process.close();
            throw e;
        }
    }

    @Override
    public void close() {
        process.close();
    }
}
