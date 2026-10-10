package com.rpsbattle.rules.classic;

import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.rule.RuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicRuleSet Tests")
class ClassicRuleSetTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    @Test
    @DisplayName("initializes all rule components and default max turns")
    void testRuleSetComponents() {
        RuleSet ruleSet = new ClassicRuleSet(P1, P2);

        assertThat(ruleSet.name()).isEqualTo("Classic");
        assertThat(ruleSet.maxTurns()).isEqualTo(ClassicRuleSet.DEFAULT_MAX_TURNS);
        assertThat(ruleSet.movementRule()).isNotNull();
        assertThat(ruleSet.combatRule()).isNotNull();
        assertThat(ruleSet.placementRule()).isNotNull();
        assertThat(ruleSet.victoryRule()).isNotNull();
        assertThat(ruleSet.turnRule()).isNotNull();
    }

    @Test
    @DisplayName("custom maxTurns constructor")
    void testCustomMaxTurns() {
        RuleSet ruleSet = new ClassicRuleSet(P1, P2, 100);
        assertThat(ruleSet.maxTurns()).isEqualTo(100);
    }
}
