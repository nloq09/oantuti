package com.rpsbattle.online;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.MoveAction;
import com.rpsbattle.game.action.PassAction;
import com.rpsbattle.game.action.SurrenderAction;
import com.rpsbattle.game.engine.GameTransition;
import com.rpsbattle.game.engine.InvalidActionException;
import com.rpsbattle.agent.PlayerAgent;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.match.Match;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * One in-memory demo. All mutable match access is serialized by this service.
 */
@Service
public class DemoMatchService {
    public static final String MATCH_ID = "demo-match";
    public static final PlayerId P1 = PlayerId.of("P1");
    public static final PlayerId P2 = PlayerId.of("P2");
    private final ClassicRuleSet rules = new ClassicRuleSet(P1, P2);
    private final LegalActionGenerator generator = new LegalActionGenerator(rules);
    private final Match match;
    private final Map<PlayerId, SubmittedActionAgent> agents = Map.of(P1, new SubmittedActionAgent(), P2, new SubmittedActionAgent());

    public DemoMatchService() {
        var initial = demoLayout();
        match = new Match(initial, rules, agents.get(P1), agents.get(P2));
        match.start();
    }

    /**
     * Sparse pre-deployed scenario for quick movement/combat, not a placement phase.
     */
    private static GameState demoLayout() {
        Board board = Board.defaultBoard();
        board.placePiece(Piece.alive("P1-R1", P1, PieceType.ROCK, new Position(6, 2)));
        board.placePiece(Piece.alive("P1-P1", P1, PieceType.PAPER, new Position(7, 6)));
        board.placePiece(Piece.alive("P2-S1", P2, PieceType.SCISSORS, new Position(2, 2)));
        board.placePiece(Piece.alive("P2-R1", P2, PieceType.ROCK, new Position(1, 6)));
        var players = List.of(new Player(P1, "Player 1", List.of("P1-R1", "P1-P1")),
                new Player(P2, "Player 2", List.of("P2-S1", "P2-R1")));
        return GameState.initial(MATCH_ID, board, players).withPhase(GamePhase.READY);
    }

    public synchronized GameState state() {
        return match.state();
    }

    public synchronized List<Action> legalActions(PlayerId player) {
        var state = match.state();
        List<Action> actions = new ArrayList<>(generator.getLegalActions(state, player));
        for (Action command : List.of(PassAction.of(player), SurrenderAction.of(player))) {
            if (rules.turnRule().canAct(state, player, command)) actions.add(command);
        }
        return List.copyOf(actions);
    }

    public boolean hasPlayer(PlayerId player) {
        return P1.equals(player) || P2.equals(player);
    }

    public synchronized GameTransition submitAction(PlayerId player, long sequence, Action action) {
        if (!hasPlayer(player)) throw new DemoRequestException("PLAYER_NOT_FOUND");
        if (match.state().isFinished()) throw new DemoRequestException("MATCH_FINISHED");
        if (sequence != match.state().sequence()) throw new DemoRequestException("INVALID_SEQUENCE");
        if (!match.state().currentPlayerId().equals(player)) throw new DemoRequestException("NOT_YOUR_TURN");
        if (action == null || !action.playerId().equals(player)) throw new DemoRequestException("INVALID_ACTION");
        SubmittedActionAgent agent = agents.get(player);
        agent.pending = action;
        try {
            return match.playTurn();
        } catch (InvalidActionException failure) {
            throw new DemoRequestException(action instanceof MoveAction ? "INVALID_MOVE" : "INVALID_ACTION");
        } finally {
            agent.pending = null;
        }
    }

    /**
     * Bridges one submitted action into the existing Match orchestration.
     */
    private static final class SubmittedActionAgent implements PlayerAgent {
        private Action pending;

        @Override
        public Action chooseAction(PlayerView view, List<Action> legalActions) {
            if (pending == null) throw new IllegalStateException("No submitted action");
            return pending;
        }
    }
}
