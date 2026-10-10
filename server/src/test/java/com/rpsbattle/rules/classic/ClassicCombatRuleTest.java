package com.rpsbattle.rules.classic;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.rule.CombatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassicCombatRule Tests")
class ClassicCombatRuleTest {

    private static final PlayerId P1 = PlayerId.of("P1");
    private static final PlayerId P2 = PlayerId.of("P2");

    private ClassicCombatRule rule;

    @BeforeEach
    void setUp() {
        rule = new ClassicCombatRule();
    }

    private Piece piece(PieceType type, PlayerId owner) {
        return Piece.alive("id", owner, type, new Position(0, 0));
    }

    // Full 3x3 combat matrix — all 9 combinations
    @ParameterizedTest(name = "{0} vs {1} → {2}")
    @CsvSource({
            "ROCK,     ROCK,     BOTH_DIE",
            "ROCK,     PAPER,    DEFENDER_WIN",
            "ROCK,     SCISSORS, ATTACKER_WIN",
            "PAPER,    ROCK,     ATTACKER_WIN",
            "PAPER,    PAPER,    BOTH_DIE",
            "PAPER,    SCISSORS, DEFENDER_WIN",
            "SCISSORS, ROCK,     DEFENDER_WIN",
            "SCISSORS, PAPER,    ATTACKER_WIN",
            "SCISSORS, SCISSORS, BOTH_DIE"
    })
    @DisplayName("combat matrix")
    void combatMatrix(String attackerType, String defenderType, String expectedResult) {
        Piece attacker = piece(PieceType.valueOf(attackerType), P1);
        Piece defender = piece(PieceType.valueOf(defenderType), P2);

        CombatResult result = rule.resolve(attacker, defender);

        assertThat(result).isEqualTo(CombatResult.valueOf(expectedResult));
    }

    // Named individual tests for readability
    @Test @DisplayName("ROCK beats SCISSORS → ATTACKER_WIN")
    void rockBeatsScissors() {
        assertThat(rule.resolve(piece(PieceType.ROCK, P1), piece(PieceType.SCISSORS, P2)))
                .isEqualTo(CombatResult.ATTACKER_WIN);
    }

    @Test @DisplayName("SCISSORS beats PAPER → ATTACKER_WIN")
    void scissorsBeatsPaper() {
        assertThat(rule.resolve(piece(PieceType.SCISSORS, P1), piece(PieceType.PAPER, P2)))
                .isEqualTo(CombatResult.ATTACKER_WIN);
    }

    @Test @DisplayName("PAPER beats ROCK → ATTACKER_WIN")
    void paperBeatsRock() {
        assertThat(rule.resolve(piece(PieceType.PAPER, P1), piece(PieceType.ROCK, P2)))
                .isEqualTo(CombatResult.ATTACKER_WIN);
    }

    @Test @DisplayName("ROCK vs PAPER → DEFENDER_WIN")
    void rockLosesToPaper() {
        assertThat(rule.resolve(piece(PieceType.ROCK, P1), piece(PieceType.PAPER, P2)))
                .isEqualTo(CombatResult.DEFENDER_WIN);
    }

    @Test @DisplayName("PAPER vs SCISSORS → DEFENDER_WIN")
    void paperLosesToScissors() {
        assertThat(rule.resolve(piece(PieceType.PAPER, P1), piece(PieceType.SCISSORS, P2)))
                .isEqualTo(CombatResult.DEFENDER_WIN);
    }

    @Test @DisplayName("SCISSORS vs ROCK → DEFENDER_WIN")
    void scissorsLosesToRock() {
        assertThat(rule.resolve(piece(PieceType.SCISSORS, P1), piece(PieceType.ROCK, P2)))
                .isEqualTo(CombatResult.DEFENDER_WIN);
    }

    @Test @DisplayName("ROCK vs ROCK → BOTH_DIE")
    void rockVsRock() {
        assertThat(rule.resolve(piece(PieceType.ROCK, P1), piece(PieceType.ROCK, P2)))
                .isEqualTo(CombatResult.BOTH_DIE);
    }

    @Test @DisplayName("PAPER vs PAPER → BOTH_DIE")
    void paperVsPaper() {
        assertThat(rule.resolve(piece(PieceType.PAPER, P1), piece(PieceType.PAPER, P2)))
                .isEqualTo(CombatResult.BOTH_DIE);
    }

    @Test @DisplayName("SCISSORS vs SCISSORS → BOTH_DIE")
    void scissorsVsScissors() {
        assertThat(rule.resolve(piece(PieceType.SCISSORS, P1), piece(PieceType.SCISSORS, P2)))
                .isEqualTo(CombatResult.BOTH_DIE);
    }
}
