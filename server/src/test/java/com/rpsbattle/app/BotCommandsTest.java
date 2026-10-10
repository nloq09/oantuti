package com.rpsbattle.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class BotCommandsTest {
    @TempDir
    Path directory;

    @Test
    void resolvesExecutableOrSeededDemoWithoutShellParsing() {
        assertThat(BotCommands.resolve("botA")).containsExactly("botA");
        assertThat(BotCommands.resolve("demo:123")).contains("-cp", "com.rpsbattle.bot.DemoBotApplication", "123");
        assertThatThrownBy(() -> BotCommands.resolve("demo:bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonCommandPreservesSpacesAndSeparateArguments() throws Exception {
        Path file = directory.resolve("bot A.json");
        Files.writeString(file, "[\"python\",\"folder with spaces/bot.py\",\"--seed\",\"42\"]");
        assertThat(BotCommands.resolve(file.toString())).containsExactly("python", "folder with spaces/bot.py", "--seed", "42");
    }

    @Test
    void rejectsInvalidCommandFiles() throws Exception {
        Path file = directory.resolve("bad.json");
        for (String json : new String[]{"[]", "{}", "[1]", "[\"\"]", "not-json", "[\"bot\"] {}"}) {
            Files.writeString(file, json);
            assertThatThrownBy(() -> BotCommands.resolve(file.toString())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> BotCommands.resolve(directory.resolve("missing.json").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
