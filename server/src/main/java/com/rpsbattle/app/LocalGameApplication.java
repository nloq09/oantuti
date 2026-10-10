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
import java.time.Duration;

public final class LocalGameApplication {
    public static void main(String[] args) {
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        int code = execute(args, new InputStreamReader(System.in, StandardCharsets.UTF_8), output);
        if (code != 0) System.exit(code);
    }

    public static int execute(String[] args, Reader input, Writer destination) {
        PrintWriter output = new PrintWriter(destination, true);
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("human-vs-human"))) {
                run(input, output);
            } else if ((args.length == 3 || args.length == 5) && args[0].equalsIgnoreCase("bot-vs-bot")) {
                long timeoutMs = 2000;
                if (args.length == 5) {
                    if (!args[3].equals("--timeout-ms")) throw new IllegalArgumentException("Expected --timeout-ms");
                    timeoutMs = Long.parseLong(args[4]);
                }
                new LocalMatchRunner().run(BotCommands.resolve(args[1]), BotCommands.resolve(args[2]),
                        Duration.ofMillis(timeoutMs), output);
            } else {
                output.println("Usage: human-vs-human | bot-vs-bot <botA> <botB> [--timeout-ms 2000]");
                return 2;
            }
            return 0;
        } catch (IllegalStateException | IllegalArgumentException | UncheckedIOException e) {
            output.println("Game stopped: " + e.getMessage());
            return 1;
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
