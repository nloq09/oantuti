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
        var upload=uploads.upload("Ready check",new org.springframework.mock.web.MockMultipartFile("file",language.filename,"application/octet-stream",Files.readAllBytes(source)));
        uploads.validateRegistration(upload);
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

    @ParameterizedTest @EnumSource(value=BotLanguage.class,names={"PYTHON","CPP","JAVA"})
    void readinessRejectsSyntaxErrorsAndPythonValidationDoesNotExecuteUserCode(BotLanguage language) {
        var uploads=new BotUploadService(new ObjectMapper(),root.toString(),true);assumeTrue(uploads.available(language));
        var invalid=uploads.upload("Broken",new org.springframework.mock.web.MockMultipartFile("file",language.filename,"text/plain","this is invalid source @@@".getBytes()));
        assertEquals("BOT_BUILD_ERROR",assertThrows(com.rpsbattle.online.DemoRequestException.class,()->uploads.validateRegistration(invalid)).getMessage());
        if(language==BotLanguage.PYTHON) {
            var valid=uploads.upload("Parse only",new org.springframework.mock.web.MockMultipartFile("file","main.py","text/plain","raise RuntimeError('must not execute during readiness')".getBytes()));
            assertDoesNotThrow(()->uploads.validateRegistration(valid));
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

    @org.junit.jupiter.api.Test void lockedPythonAgentRunsOriginalCopyAfterLiveSourceIsReplaced() throws Exception {
        var uploads=new BotUploadService(new ObjectMapper(),root.toString(),true);assumeTrue(uploads.available(BotLanguage.PYTHON));
        var source=Path.of("../client/assets/bot-examples/main.py").toAbsolutePath().normalize();
        var bot=uploads.upload("Locked",new org.springframework.mock.web.MockMultipartFile("file","main.py","text/plain",Files.readAllBytes(source)));var hash=uploads.freeze(bot.id(),uploads.sourceHash(bot.id()));
        Files.writeString(uploads.source(bot),"invalid replaced source @@@");
        var p1=PlayerId.of("P1");var p2=PlayerId.of("P2");var state=ClassicGameSetup.create("LOCKED",p1,p2).withPhase(GamePhase.PLAYING);var actions=new LegalActionGenerator(new ClassicRuleSet(p1,p2)).getLegalActions(state,p1);
        try(var agent=uploads.lockedAgent(bot.id(),bot.token(),hash)){assertEquals(actions.getFirst(),agent.chooseAction(new PlayerView(state,p1),actions));}
    }
}
