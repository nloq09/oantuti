package com.rpsbattle.game.engine;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.RuleSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Enumerates playable moves without changing the input snapshot.
 */
public final class LegalActionGenerator {
    private final RuleSet rules;

    public LegalActionGenerator(RuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Returns movement actions, including attacks, in deterministic order.
     * Placement, pass and surrender are separate commands, not generated moves.
     * Enumerating every cell keeps the generator independent of movement geometry.
     */
    public List<Action> getLegalActions(GameState state, PlayerId player) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(player, "player");
        if (state.isFinished() || state.findPlayer(player).isEmpty()) return List.of();

        Board board = state.board();
        var pieces = board.allPieces().stream()
                .filter(piece -> piece.isAlive() && piece.ownedBy(player))
                .sorted(Comparator.comparingInt((Piece piece) -> piece.position().row())
                        .thenComparingInt(piece -> piece.position().col())
                        .thenComparing(Piece::id))
                .toList();
        List<Action> actions = new ArrayList<>();
        for (Piece piece : pieces) {
            for (int row = 0; row < board.rows(); row++) {
                for (int col = 0; col < board.cols(); col++) {
                    Position to = new Position(row, col);
                    if (piece.position().equals(to)) continue;
                    // Match the engine's ownership invariant even for permissive variants.
                    if (board.getPiece(to).filter(target -> target.ownedBy(player)).isPresent()) continue;
                    MoveAction action = MoveAction.of(player, piece.id(), piece.position(), to);
                    if (rules.turnRule().canAct(state, player, action)
                            && rules.movementRule().canMove(piece, piece.position(), to, board)) {
                        actions.add(action);
                    }
                }
            }
        }
        return List.copyOf(actions);
    }
}
