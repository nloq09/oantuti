package com.rpsbattle.agent;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.PassAction;
import com.rpsbattle.game.action.SurrenderAction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/** Minimal local adapter: choose a listed action by number, pass or surrender. */
public final class ConsoleHumanAgent implements PlayerAgent {
    private final BufferedReader input;
    private final PrintWriter output;

    public ConsoleHumanAgent(Reader input, Writer output) {
        Objects.requireNonNull(input, "input");
        this.input = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        this.output = new PrintWriter(Objects.requireNonNull(output, "output"), true);
    }

    @Override
    public Action chooseAction(PlayerView view, List<Action> legalActions) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(legalActions, "legalActions");
        output.println("Player " + view.playerId() + " — choose action number, pass, or surrender:");
        for (int i = 0; i < legalActions.size(); i++) output.println((i + 1) + ": " + legalActions.get(i));
        while (true) {
            output.print("> ");
            output.flush();
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read player input", e);
            }
            if (line == null) throw new IllegalStateException("Console input ended before an action was selected");
            line = line.trim();
            if (line.equalsIgnoreCase("surrender")) return SurrenderAction.of(view.playerId());
            if (line.equalsIgnoreCase("pass")) return PassAction.of(view.playerId());
            try {
                int index = Integer.parseInt(line) - 1;
                if (index >= 0 && index < legalActions.size()) return legalActions.get(index);
            } catch (NumberFormatException ignored) {
                // Retry malformed input; game rules are validated only by the engine.
            }
            output.println("Enter a listed action number, pass, or surrender.");
        }
    }
}
