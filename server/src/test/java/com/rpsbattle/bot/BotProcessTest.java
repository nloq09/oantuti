package com.rpsbattle.bot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.rules.classic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class BotProcessTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    private final PlayerView view = new PlayerView(ClassicGameSetup.create("M1", P1, P2).withPhase(GamePhase.PLAYING), P1);
    private final List<com.rpsbattle.game.action.Action> legal =
            new LegalActionGenerator(new ClassicRuleSet(P1, P2)).getLegalActions(view.state(), P1);

    private LocalProcessRunner runner(String mode) {
        String classpath = List.of(TestBotProcessMain.class, ObjectMapper.class, JsonFactory.class, JsonProperty.class)
                .stream().map(type -> {
                    try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                }).distinct().collect(Collectors.joining(File.pathSeparator));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new LocalProcessRunner(List.of(java, "-cp", classpath, TestBotProcessMain.class.getName(), mode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "stderr"})
    void separateProcessHandlesMultipleTurnsAndCloses(String mode) {
        LocalProcessRunner runner = runner(mode);
        try (BotAgent agent = new BotAgent(runner, Duration.ofSeconds(5))) {
            assertThat(agent.chooseAction(view, legal)).isEqualTo(legal.get(0));
            assertThat(runner.isAlive()).isTrue();
            var next = new PlayerView(view.state().withIncrementedSequence(), P1);
            assertThat(agent.chooseAction(next, legal)).isEqualTo(legal.get(0));
        }
        assertThat(runner.isAlive()).isFalse();
        runner.close();
        assertThatThrownBy(runner::start).isInstanceOf(BotProtocolException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exit", "partial", "oversize", "bad-json", "stale"})
    void badOrTerminatedBotFailsClearlyAndIsStopped(String mode) {
        LocalProcessRunner runner = runner(mode);
        try (BotAgent agent = new BotAgent(runner, Duration.ofSeconds(5))) {
            assertThatThrownBy(() -> agent.chooseAction(view, legal)).isInstanceOf(BotProtocolException.class);
            assertThat(runner.isAlive()).isFalse();
        }
    }

    @Test
    void hangingBotTimesOutAndIsTerminated() {
        LocalProcessRunner runner = runner("hang");
        try (BotAgent agent = new BotAgent(runner, Duration.ofMillis(200))) {
            assertThatThrownBy(() -> agent.chooseAction(view, legal)).isInstanceOf(BotProtocolException.class)
                    .hasMessageContaining("timed out");
            assertThat(runner.isAlive()).isFalse();
        }
    }

    @Test
    void missingExecutableIsReported() {
        try (LocalProcessRunner runner = new LocalProcessRunner(List.of("rps-nonexistent-bot-executable"))) {
            assertThatThrownBy(runner::start).isInstanceOf(BotProtocolException.class).hasMessageContaining("Cannot start");
        }
    }

    @Test
    void matchStillValidatesMovesFromExternalBot() {
        try (BotAgent agent = new BotAgent(runner("illegal"), Duration.ofSeconds(5))) {
            var match = new com.rpsbattle.match.Match(view.state(), new ClassicRuleSet(P1, P2),
                    agent, (v, a) -> a.get(0));
            match.start();
            var before = match.state();
            assertThatThrownBy(match::playTurn).isInstanceOf(com.rpsbattle.game.engine.InvalidActionException.class)
                    .hasMessageContaining("Illegal movement");
            assertThat(match.state()).isSameAs(before);
        }
    }
}
