package com.rpsbattle.app;

import com.rpsbattle.agent.ConsoleHumanAgent;
import com.rpsbattle.game.engine.InvalidActionException;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.match.Match;
import com.rpsbattle.match.MatchStatus;
import com.rpsbattle.rules.classic.ClassicGameSetup;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class LocalGameApplication {
    public static void main(String[] args) {
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("human-vs-human"))) {
            output.println("Usage: run --args=human-vs-human (or run without arguments)");
            return;
        }
        try {
            run(new InputStreamReader(System.in, StandardCharsets.UTF_8), output);
        } catch (IllegalStateException | UncheckedIOException e) {
            output.println("Game stopped: " + e.getMessage());
        }
    }

    public static GameState run(Reader input, Writer destination) {
        BufferedReader shared = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        PrintWriter output = new PrintWriter(destination, true);
        PlayerId p1 = PlayerId.of("P1"), p2 = PlayerId.of("P2");
        Match match = new Match(ClassicGameSetup.create("LOCAL-001", p1, p2), new ClassicRuleSet(p1, p2),
                new ConsoleHumanAgent(shared, output), new ConsoleHumanAgent(shared, output));
        ConsoleBoardRenderer renderer = new ConsoleBoardRenderer();
        output.println("RPS Battle — Human vs Human; default deployment: 6 of each type per player.");
        match.start();
        while (match.status() == MatchStatus.RUNNING) {
            renderer.render(match.state(), output);
            try {
                match.playTurn();
            } catch (InvalidActionException e) {
                output.println("Invalid action: " + e.getMessage() + ". Try again.");
            }
        }
        renderer.render(match.state(), output);
        output.println("Result: " + match.state().result().orElseThrow());
        output.println("Remaining: P1=" + match.state().aliveCount(p1) + ", P2=" + match.state().aliveCount(p2));
        output.flush();
        return match.state();
    }
}
