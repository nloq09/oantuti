package com.rpsbattle.app;

import com.rpsbattle.bot.*;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.match.Match;
import com.rpsbattle.rules.classic.*;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.nio.file.Path;
import com.rpsbattle.replay.ReplayService;

/** Owns both process lifetimes; gameplay is delegated to the existing Match. */
public final class LocalMatchRunner {
    public GameState run(List<String> commandA, List<String> commandB, Duration timeout, PrintWriter output) {
        return run(commandA, commandB, timeout, output, null);
    }

    public GameState run(List<String> commandA, List<String> commandB, Duration timeout, PrintWriter output, Path export) {
        validate(timeout, output);
        // Construct transports only after validating both commands.
        if (commandA.isEmpty() || commandB.isEmpty()
                || commandA.stream().anyMatch(String::isBlank) || commandB.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Both bot commands must contain nonblank arguments");
        }
        return run(new LocalProcessRunner(commandA), new LocalProcessRunner(commandB), timeout, output, export);
    }

    public GameState run(BotProcess processA, BotProcess processB, Duration timeout, PrintWriter output) {
        return run(processA, processB, timeout, output, null);
    }

    private GameState run(BotProcess processA, BotProcess processB, Duration timeout, PrintWriter output, Path export) {
        validate(timeout, output);
        Objects.requireNonNull(processA, "processA");
        Objects.requireNonNull(processB, "processB");
        PlayerId p1 = PlayerId.of("P1"), p2 = PlayerId.of("P2");
        long started = System.nanoTime();
        GameState finished;
        try (processA; processB) {
            processA.start();
            processB.start();
            Match match = new Match(ClassicGameSetup.create("BOT-LOCAL-001", p1, p2), new ClassicRuleSet(p1, p2),
                    new BotAgent(processA, timeout), new BotAgent(processB, timeout));
            finished = match.runToCompletion();
            if (export != null) new ReplayService().write(export, match);
        }
        output.println("Match ID: " + finished.matchId());
        output.println("Winner: " + finished.result().orElseThrow().winner().map(PlayerId::value).orElse("DRAW"));
        output.println("Total turns: " + finished.turnNumber());
        output.println("Remaining pieces: P1=" + finished.aliveCount(p1) + ", P2=" + finished.aliveCount(p2));
        output.printf("Execution time: %.3f ms%n", (System.nanoTime() - started) / 1_000_000.0);
        output.flush();
        return finished;
    }

    private void validate(Duration timeout, PrintWriter output) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(output, "output");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Timeout must be positive");
    }
}
