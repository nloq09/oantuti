package com.rpsbattle.tournament;
import com.rpsbattle.tournament.ranking.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ScoringRuleTest {@Test void classicPoints(){var rule=new ClassicScoringRule();assertThat(rule.pointsForWin()).isEqualTo(3);assertThat(rule.pointsForDraw()).isEqualTo(1);assertThat(rule.pointsForLoss()).isZero();}}
