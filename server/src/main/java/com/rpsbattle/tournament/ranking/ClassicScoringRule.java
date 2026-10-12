package com.rpsbattle.tournament.ranking;
import org.springframework.stereotype.Component;
@Component
public class ClassicScoringRule implements ScoringRule {
    public int pointsForWin(){return 3;} public int pointsForDraw(){return 1;} public int pointsForLoss(){return 0;}
}
