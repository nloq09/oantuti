package com.rpsbattle.agent;

import com.rpsbattle.game.action.Action;

import java.util.List;

@FunctionalInterface
public interface PlayerAgent {
    Action chooseAction(PlayerView view, List<Action> legalActions);
}
