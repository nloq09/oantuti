package com.rpsbattle.agent;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.PassAction;
import com.rpsbattle.game.action.SurrenderAction;
import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.InvalidActionException;
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
        output.println("Player " + view.playerId() + " — move C7 C6, C7 C6, action number, pass, or surrender:");
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
            String[] tokens = line.split("\\s+");
            int offset = tokens[0].equalsIgnoreCase("move") ? 1 : 0;
            if (tokens.length == offset + 2) {
                Position from;
                Position to;
                try {
                    from = coordinate(tokens[offset]);
                    to = coordinate(tokens[offset + 1]);
                } catch (IllegalArgumentException e) {
                    output.println("Use coordinates such as C7 C6.");
                    continue;
                }
                var piece = view.state().board().getPiece(from)
                        .orElseThrow(() -> new InvalidActionException("No piece at " + tokens[offset]));
                try {
                    return MoveAction.of(view.playerId(), piece.id(), from, to);
                } catch (IllegalArgumentException e) {
                    output.println(e.getMessage());
                    continue;
                }
            }
            try {
                int index = Integer.parseInt(line) - 1;
                if (index >= 0 && index < legalActions.size()) return legalActions.get(index);
            } catch (NumberFormatException ignored) {
                // Retry malformed input; game rules are validated only by the engine.
            }
            output.println("Enter a listed action number, coordinates, pass, or surrender.");
        }
    }

    private Position coordinate(String token) {
        if (!token.matches("[a-zA-Z][1-9][0-9]*")) throw new IllegalArgumentException("Invalid coordinate");
        return new Position(Integer.parseInt(token.substring(1)) - 1,
                Character.toUpperCase(token.charAt(0)) - 'A');
    }
}
