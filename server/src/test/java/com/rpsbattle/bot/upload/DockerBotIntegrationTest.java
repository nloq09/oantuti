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
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs real containers when Docker Linux and the corresponding image are provisioned. */
class DockerBotIntegrationTest {
    @TempDir Path root;
    @ParameterizedTest @EnumSource(value=BotLanguage.class,names={"PYTHON","CPP","JAVA"})
    void sampleRunsMultipleTurnsInRestrictedContainer(BotLanguage language)throws Exception{
        var uploads=new BotUploadService(new ObjectMapper(),root.toString(),true);
        assumeTrue(uploads.available(language),"Docker Linux/image not provisioned for "+language);
        Path source=Path.of("../client/assets/bot-examples").resolve(language.filename).toAbsolutePath().normalize();
        var p1=PlayerId.of("P1");var p2=PlayerId.of("P2");
        var state=ClassicGameSetup.create("DOCKER",p1,p2).withPhase(GamePhase.PLAYING);
        var actions=new LegalActionGenerator(new ClassicRuleSet(p1,p2)).getLegalActions(state,p1);
        var protocol=new BotProtocol();
        try(var process=new DockerBotProcess(source,language)){
            for(int i=0;i<2;i++){
                var turn=state.withSequence(state.sequence()+i);
                var response=process.exchange(protocol.encodeTurn(new PlayerView(turn,p1),actions),Duration.ofSeconds(2));
                assertEquals(actions.getFirst(),protocol.decodeResponse(response,turn.sequence(),p1));
            }
        }
    }
}
