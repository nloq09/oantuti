package com.rpsbattle.agent;

import com.rpsbattle.game.action.Action;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Randomness belongs to this agent and is supplied once, never per turn.
 */
public final class RandomBotAgent implements PlayerAgent {
    private final RandomGenerator random;

    public RandomBotAgent(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public RandomBotAgent(long seed) {
        this(new Random(seed));
    }

    @Override
    public Action chooseAction(PlayerView view, List<Action> legalActions) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(legalActions, "legalActions");
        if (legalActions.isEmpty()) throw new IllegalArgumentException("No legal actions supplied");
        return legalActions.get(random.nextInt(legalActions.size()));
    }
}
