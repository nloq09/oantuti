package com.rpsbattle.bot.upload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class BotVersionLockTest {
    @TempDir Path root;
    @Test void immutableCopySurvivesSourceEditAndNewUploadButTamperingIsRejected() throws Exception {
        var service=new BotUploadService(new ObjectMapper(),root.toString(),false);
        var bot=service.upload("Original",new MockMultipartFile("file","main.py","text/plain","print(1)".getBytes()));
        String hash=service.sourceHash(bot.id());assertThat(hash).matches("[a-f0-9]{64}");assertThat(service.freeze(bot.id(),hash)).isEqualTo(hash);
        Files.writeString(service.source(bot),"print(2)");service.verifyFrozen(bot.id(),hash);
        service.upload("New version",new MockMultipartFile("file","main.py","text/plain","print(3)".getBytes()));service.verifyFrozen(bot.id(),hash);
        assertThatThrownBy(()->service.freeze(bot.id(),hash)).hasMessage("BOT_ARTIFACT_CHANGED");
        Files.writeString(root.resolve(bot.id()).resolve("versions").resolve(hash).resolve("main.py"),"changed");
        assertThatThrownBy(()->service.verifyFrozen(bot.id(),hash)).hasMessage("BOT_ARTIFACT_CHANGED");
        assertThatThrownBy(()->service.verifyFrozen(bot.id(),"../escape")).hasMessage("BOT_ARTIFACT_CHANGED");
    }
}
