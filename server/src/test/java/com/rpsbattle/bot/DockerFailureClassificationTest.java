package com.rpsbattle.bot;
import com.rpsbattle.bot.upload.DockerBotProcess;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DockerFailureClassificationTest {
    @Test void daemonAndRunnerFailuresDoNotBecomeBotForfeits(){
        assertThat(DockerBotProcess.infrastructureFailure(1,false)).isTrue();
        for(int code:new int[]{125,126,127})assertThat(DockerBotProcess.infrastructureFailure(code,true)).isTrue();
        for(int code:new int[]{0,1,137,-1})assertThat(DockerBotProcess.infrastructureFailure(code,true)).isFalse();
    }
}
