package com.rpsbattle.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.bot.DemoBotApplication;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A bot spec is an executable, a JSON command file, or demo:<seed>. */
public final class BotCommands {
    private BotCommands() {}

    public static List<String> resolve(String spec) {
        if (spec.startsWith("demo:")) {
            long seed = Long.parseLong(spec.substring(5));
            return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), DemoBotApplication.class.getName(), Long.toString(seed));
        }
        if (spec.endsWith(".json")) {
            try {
                var json = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(Path.of(spec).toFile());
                if (json == null || !json.isArray() || json.isEmpty()) throw new IllegalArgumentException("Bot command file must be a nonempty JSON array");
                List<String> command = new ArrayList<>();
                for (var argument : json) {
                    if (!argument.isTextual() || argument.asText().isBlank()) throw new IllegalArgumentException("Bot command arguments must be nonblank strings");
                    command.add(argument.asText());
                }
                return List.copyOf(command);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read bot command file: " + spec, e);
            }
        }
        if (spec.isBlank()) throw new IllegalArgumentException("Bot executable must not be blank");
        return List.of(spec);
    }
}
