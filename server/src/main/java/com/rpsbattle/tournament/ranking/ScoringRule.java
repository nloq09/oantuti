package com.rpsbattle.tournament.ranking;
public interface ScoringRule {
    int pointsForWin(); int pointsForDraw(); int pointsForLoss();
}
