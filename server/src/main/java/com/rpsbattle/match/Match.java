package com.rpsbattle.match;

import com.rpsbattle.agent.PlayerAgent;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.action.PassAction;
import com.rpsbattle.game.engine.GameEngine;
import com.rpsbattle.game.engine.GameTransition;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.engine.InvalidActionException;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

/** Single-threaded local match; initial placement is provided by the caller. */
public final class Match {
    private GameState state;
    private final GameState initialState;
    private final Long seed;
    private final List<GameEvent> events = new ArrayList<>();
    private final RuleSet ruleSet;
    private final Map<PlayerId, PlayerAgent> agents;
    private final GameEngine engine;
    private final LegalActionGenerator generator;
    private MatchStatus status = MatchStatus.CREATED;

    /** Agents correspond to players[0] and players[1] in the supplied state. */
    public Match(GameState initialState, RuleSet ruleSet, PlayerAgent player1, PlayerAgent player2) {
        this(initialState, ruleSet, player1, player2, null);
    }

    public Match(GameState initialState, RuleSet ruleSet, PlayerAgent player1, PlayerAgent player2, Long seed) {
        state = Objects.requireNonNull(initialState, "initialState");
        this.initialState = state;
        this.seed = seed;
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
        if (state.players().size() != 2 || state.players().get(0).id().equals(state.players().get(1).id())) {
            throw new IllegalArgumentException("Match requires two distinct players");
        }
        if (state.phase() != GamePhase.READY && state.phase() != GamePhase.PLAYING) {
            throw new IllegalArgumentException("Match requires a READY or PLAYING state with placement completed");
        }
        if (state.findPlayer(state.currentPlayerId()).isEmpty() || state.result().isPresent()) {
            throw new IllegalArgumentException("Invalid current player or premature result");
        }
        agents = Map.of(state.players().get(0).id(), Objects.requireNonNull(player1, "player1"),
                state.players().get(1).id(), Objects.requireNonNull(player2, "player2"));
        engine = new GameEngine(ruleSet);
        generator = new LegalActionGenerator(ruleSet);
    }

    public String matchId() { return state.matchId(); }
    public GameState state() { return state; }
    public RuleSet ruleSet() { return ruleSet; }
    public MatchStatus status() { return status; }
    public GameState initialState() { return initialState; }
    public Long seed() { return seed; }
    public List<GameEvent> events() { return List.copyOf(events); }

    public void start() {
        if (status != MatchStatus.CREATED) throw new IllegalStateException("Match has already started");
        GameState started = state.withPhase(GamePhase.PLAYING);
        var result = ruleSet.victoryRule().checkResult(started);
        events.add(new GameEvent.MatchStarted(started.sequence()));
        if (result.isPresent()) {
            state = started.withResult(result.get()).withPhase(GamePhase.FINISHED);
            status = MatchStatus.FINISHED;
            events.add(new GameEvent.MatchFinished(state.sequence(), result.get()));
        } else {
            state = started;
            status = MatchStatus.RUNNING;
        }
    }

    /** Invalid agent actions propagate without changing the match; caller may retry. */
    public GameTransition playTurn() {
        if (status != MatchStatus.RUNNING) throw new IllegalStateException("Match is not running");
        PlayerId player = state.currentPlayerId();
        List<Action> legalActions = generator.getLegalActions(state, player);
        if (legalActions.isEmpty()) {
            PassAction pass = PassAction.of(player);
            if (!ruleSet.turnRule().canAct(state, player, pass)) {
                throw new IllegalStateException("No legal moves and passing is forbidden for " + player);
            }
            legalActions = List.of(pass);
        }
        Action action = agents.get(player).chooseAction(new PlayerView(state, player), legalActions);
        if (action == null) throw new InvalidActionException("Agent returned no action");
        GameTransition transition = engine.applyAction(state, player, action);
        // Append only after validation succeeds; rejected actions leave no log entries.
        events.add(new GameEvent.TurnStarted(transition.newState().sequence(), player));
        events.addAll(transition.events());
        state = transition.newState();
        events.add(new GameEvent.TurnEnded(state.sequence(), state.currentPlayerId(), state.turnNumber()));
        if (state.isFinished()) {
            status = MatchStatus.FINISHED;
            events.add(new GameEvent.MatchFinished(state.sequence(), state.result().orElseThrow()));
        }
        return transition;
    }

    public GameState runToCompletion() {
        if (status == MatchStatus.CREATED) start();
        while (status == MatchStatus.RUNNING) playTurn();
        return state;
    }
}
