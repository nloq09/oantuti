package com.rpsbattle.app;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.state.GameState;
import java.io.PrintWriter;

public final class ConsoleBoardRenderer {
    public void render(GameState state, PrintWriter output) {
        Board board = state.board();
        output.println("Match " + state.matchId() + " | Turns: " + state.turnNumber()
                + " | Current: " + state.currentPlayerId());
        output.print("    ");
        for (int col = 0; col < board.cols(); col++) output.printf("%3s", (char) ('A' + col));
        output.println();
        for (int row = 0; row < board.rows(); row++) {
            output.printf("%3d ", row + 1);
            for (int col = 0; col < board.cols(); col++) {
                String symbol = board.getPiece(new Position(row, col)).map(piece -> {
                    String type = switch (piece.type()) {
                        case ROCK -> "R";
                        case PAPER -> "P";
                        case SCISSORS -> "S";
                    };
                    return type + (piece.ownerId().equals(state.players().get(0).id()) ? "1" : "2");
                }).orElse(".");
                output.printf("%3s", symbol);
            }
            output.println();
        }
        output.println("R = Rock, P = Paper, S = Scissors; 1/2 = player");
        output.flush();
    }
}
