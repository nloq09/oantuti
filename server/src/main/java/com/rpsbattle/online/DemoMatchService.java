package com.rpsbattle.online;

import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.match.Match;
import com.rpsbattle.rules.classic.ClassicGameSetup;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.springframework.stereotype.Service;

import java.util.List;

/** One in-memory demo. All mutable match access is serialized by this service. */
@Service
public class DemoMatchService {
    public static final String MATCH_ID = "demo-match";
    public static final PlayerId P1 = PlayerId.of("P1");
    public static final PlayerId P2 = PlayerId.of("P2");
    private final ClassicRuleSet rules = new ClassicRuleSet(P1, P2);
    private final LegalActionGenerator generator = new LegalActionGenerator(rules);
    private final Match match;

    public DemoMatchService() {
        var initial = ClassicGameSetup.create(MATCH_ID, P1, P2);
        match = new Match(initial, rules, (view, actions) -> {
            throw new IllegalStateException("Waiting for online action");
        }, (view, actions) -> {
            throw new IllegalStateException("Waiting for online action");
        });
        match.start();
    }

    public synchronized GameState state() { return match.state(); }

    public synchronized List<Action> legalActions(PlayerId player) {
        return generator.getLegalActions(match.state(), player);
    }

    public boolean hasPlayer(PlayerId player) { return P1.equals(player) || P2.equals(player); }
}
