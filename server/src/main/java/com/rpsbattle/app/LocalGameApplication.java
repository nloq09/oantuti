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
import java.nio.file.Path;
import com.rpsbattle.replay.ReplayService;

public final class LocalGameApplication {
    public static void main(String[] args) {
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        int code = execute(args, new InputStreamReader(System.in, StandardCharsets.UTF_8), output);
        if (code != 0) System.exit(code);
    }

    public static int execute(String[] args, Reader input, Writer destination) {
        PrintWriter output = new PrintWriter(destination, true);
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("replay")) {
                GameState replayed = new ReplayService().replay(Path.of(args[1]));
                new ConsoleBoardRenderer().render(replayed, output);
                output.println("Replay result: " + replayed.result().orElseThrow());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("human-vs-human") && args[1].equals("--export")) {
                run(input, output, Path.of(args[2]));
            } else if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("human-vs-human"))) {
                run(input, output);
            } else if ((args.length == 3 || args.length == 5 || args.length == 7) && args[0].equalsIgnoreCase("bot-vs-bot")) {
                long timeoutMs = 2000;
                Path export = null;
                boolean timeoutSeen = false;
                for (int i = 3; i < args.length; i += 2) {
                    if (args[i].equals("--timeout-ms") && !timeoutSeen) {
                        timeoutMs = Long.parseLong(args[i + 1]); timeoutSeen = true;
                    } else if (args[i].equals("--export") && export == null) {
                        export = Path.of(args[i + 1]);
                    } else throw new IllegalArgumentException("Unknown or duplicate option: " + args[i]);
                }
                new LocalMatchRunner().run(BotCommands.resolve(args[1]), BotCommands.resolve(args[2]),
                        Duration.ofMillis(timeoutMs), output, export);
            } else {
                output.println("Usage: human-vs-human [--export file.json] | bot-vs-bot <botA> <botB> [--timeout-ms 2000] [--export file.json] | replay file.json");
                return 2;
            }
            return 0;
        } catch (IllegalStateException | IllegalArgumentException | UncheckedIOException e) {
            output.println("Game stopped: " + e.getMessage());
            return 1;
        }
    }

    public static GameState run(Reader input, Writer destination) {
        return run(input, destination, null);
    }

    private static GameState run(Reader input, Writer destination, Path export) {
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
        if (export != null) new ReplayService().write(export, match);
        output.println("Result: " + match.state().result().orElseThrow());
        output.println("Remaining: P1=" + match.state().aliveCount(p1) + ", P2=" + match.state().aliveCount(p2));
        output.flush();
        return match.state();
    }
}
