package com.rpsbattle.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.bot.*;
import com.rpsbattle.game.state.GameState;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class LocalMatchRunnerTest {
    private final LocalMatchRunner runner = new LocalMatchRunner();

    private List<String> demo(long seed) {
        String classpath = List.of(DemoBotApplication.class, ObjectMapper.class, JsonFactory.class, JsonProperty.class)
                .stream().map(type -> {
                    try {
                        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).distinct().collect(Collectors.joining(File.pathSeparator));
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classpath,
                DemoBotApplication.class.getName(), Long.toString(seed));
    }

    @Test
    void twoSeparateDemoBotsFinishAndPrintRequiredSummary() {
        StringWriter output = new StringWriter();
        var a = new LocalProcessRunner(demo(1));
        var b = new LocalProcessRunner(demo(2));
        GameState result = runner.run(a, b, Duration.ofSeconds(5), new PrintWriter(output));
        assertThat(result.isFinished()).isTrue();
        assertThat(result.turnNumber()).isBetween(1, 200);
        assertThat(result.sequence()).isEqualTo(36 + result.turnNumber());
        assertThat(output.toString()).contains("Match ID: BOT-LOCAL-001", "Winner:", "Total turns:",
                "Remaining pieces: P1=", "P2=", "Execution time:");
        assertThat(a.isAlive()).isFalse();
        assertThat(b.isAlive()).isFalse();
    }

    @Test
    void sameSeedsReproduceFinalState() {
        GameState a = runner.run(demo(11), demo(22), Duration.ofSeconds(5), new PrintWriter(new StringWriter()));
        GameState b = runner.run(demo(11), demo(22), Duration.ofSeconds(5), new PrintWriter(new StringWriter()));
        assertThat(a.result()).isEqualTo(b.result());
        assertThat(a.turnNumber()).isEqualTo(b.turnNumber());
        var positionsA = a.board().allPieces().stream().map(p -> p.id() + ":" + p.position() + ":" + p.type()).sorted().toList();
        var positionsB = b.board().allPieces().stream().map(p -> p.id() + ":" + p.position() + ":" + p.type()).sorted().toList();
        assertThat(positionsA).isEqualTo(positionsB);
    }

    @Test
    void secondStartupFailureClosesBothTransportsAndPrintsNoWinner() {
        Transport a = new Transport(), b = new Transport();
        b.failStart = true;
        StringWriter output = new StringWriter();
        assertThatThrownBy(() -> runner.run(a, b, Duration.ofSeconds(1), new PrintWriter(output)))
                .isInstanceOf(BotProtocolException.class);
        assertThat(a.closed && b.closed).isTrue();
        assertThat(output.toString()).doesNotContain("Winner:");
    }

    @Test
    void responseFailureClosesBothTransports() {
        Transport a = new Transport(), b = new Transport();
        a.failResponse = true;
        assertThatThrownBy(() -> runner.run(a, b, Duration.ofSeconds(1), new PrintWriter(new StringWriter())))
                .isInstanceOf(BotProtocolException.class);
        assertThat(a.closed && b.closed).isTrue();
    }

    @Test
    void cliReportsUsageAndInvalidTimeoutWithNonzeroStatus() {
        StringWriter output = new StringWriter();
        assertThat(LocalGameApplication.execute(new String[]{"bot-vs-bot"}, new StringReader(""), output)).isEqualTo(2);
        assertThat(LocalGameApplication.execute(new String[]{"bot-vs-bot", "demo:1", "demo:2", "--timeout-ms", "0"},
                new StringReader(""), output)).isEqualTo(1);
        assertThat(output.toString()).contains("Usage:", "Timeout must be positive");
    }

    private static final class Transport implements BotProcess {
        boolean closed, started, failStart, failResponse;

        public void start() {
            started = true;
            if (failStart) throw new BotProtocolException("start failure");
        }

        public String exchange(String request, Duration timeout) {
            if (failResponse) return "invalid JSON";
            try {
                long sequence = new ObjectMapper().readTree(request).get("sequence").asLong();
                return "{\"type\":\"ACTION\",\"sequence\":" + sequence + ",\"action\":{\"type\":\"PASS\"}}";
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean isAlive() {
            return started && !closed;
        }

        public void close() {
            closed = true;
        }
    }
}
