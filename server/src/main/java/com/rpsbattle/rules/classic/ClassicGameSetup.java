package com.rpsbattle.rules.classic;

import com.rpsbattle.game.action.PlaceAction;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.GameEngine;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import java.util.List;

/** Deterministic default deployment, validated by the same engine as gameplay. */
public final class ClassicGameSetup {
    private ClassicGameSetup() {}

    public static GameState create(String matchId, PlayerId p1, PlayerId p2) {
        if (p1.equals(p2)) throw new IllegalArgumentException("Players must be distinct");
        ClassicRuleSet rules = new ClassicRuleSet(p1, p2);
        GameEngine engine = new GameEngine(rules);
        GameState state = GameState.initial(matchId, Board.defaultBoard(),
                List.of(Player.of(p1, "Player 1"), Player.of(p2, "Player 2"))).withPhase(GamePhase.PLACEMENT);
        PieceType[] types = PieceType.values();
        for (int index = 0; index < 18; index++) {
            for (PlayerId player : List.of(p1, p2)) {
                PieceType type = types[index % types.length];
                Board board = state.board();
                Position target = null;
                for (int row = 0; row < board.rows() && target == null; row++) {
                    for (int col = 0; col < board.cols(); col++) {
                        Position candidate = new Position(row, col);
                        if (rules.placementRule().canPlace(player, type, candidate, board)) {
                            target = candidate;
                            break;
                        }
                    }
                }
                if (target == null) throw new IllegalStateException("No default deployment cell for " + player);
                state = engine.applyAction(state, player,
                        PlaceAction.of(player, player + "-" + (index + 1), type, target)).newState();
            }
        }
        return state.withPhase(GamePhase.READY);
    }
}
