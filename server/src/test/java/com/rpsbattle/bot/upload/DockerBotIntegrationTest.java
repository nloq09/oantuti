package com.rpsbattle.bot.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.bot.BotProtocol;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.rules.classic.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.jar.*;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs real containers when Docker Linux and the corresponding image are provisioned.
 */
class DockerBotIntegrationTest {
    @TempDir
    Path root;

    @ParameterizedTest
    @EnumSource(BotLanguage.class)
    void sampleRunsMultipleTurnsInRestrictedContainer(BotLanguage language) throws Exception {
        var uploads = new BotUploadService(new ObjectMapper(), root.toString(), true);
        assumeTrue(uploads.available(language), "Docker Linux/image not provisioned for " + language);
        Path source = language == BotLanguage.JAR ? sampleJar() : Path.of("../client/assets/bot-examples").resolve(language.filename).toAbsolutePath().normalize();
        var p1 = PlayerId.of("P1");
        var p2 = PlayerId.of("P2");
        var state = ClassicGameSetup.create("DOCKER", p1, p2).withPhase(GamePhase.PLAYING);
        var actions = new LegalActionGenerator(new ClassicRuleSet(p1, p2)).getLegalActions(state, p1);
        var protocol = new BotProtocol();
        try (var process = new DockerBotProcess(source, language)) {
            for (int i = 0; i < 2; i++) {
                var turn = state.withSequence(state.sequence() + i);
                var response = process.exchange(protocol.encodeTurn(new PlayerView(turn, p1), actions), Duration.ofSeconds(2));
                assertEquals(actions.getFirst(), protocol.decodeResponse(response, turn.sequence(), p1));
            }
        }
    }

    private Path sampleJar() throws Exception {
        Path source = Path.of("../client/assets/bot-examples/Main.java").toAbsolutePath().normalize();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-d", root.toString(), source.toString()));
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "Main");
        Path jar = root.resolve("bot.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("Main.class"));
            Files.copy(root.resolve("Main.class"), output);
            output.closeEntry();
        }
        return jar;
    }
}
