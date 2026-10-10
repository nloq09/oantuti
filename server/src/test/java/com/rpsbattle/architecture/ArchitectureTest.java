package com.rpsbattle.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** Source-level boundaries are checked without introducing a framework dependency. */
class ArchitectureTest {
    private String source(String folder) throws IOException {
        try (var files = Files.walk(Path.of("src/main/java/com/rpsbattle", folder))) {
            StringBuilder result = new StringBuilder();
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) result.append(Files.readString(file));
            return result.toString();
        }
    }

    private List<String> imports(String folder) throws IOException {
        return Pattern.compile("(?m)^import\\s+([^;]+);").matcher(source(folder)).results()
                .map(m -> m.group(1)).toList();
    }

    @Test
    void coreHasNoInfrastructureOrAdapters() throws Exception {
        assertThat(imports("game")).noneMatch(i -> i.startsWith("java.io") || i.startsWith("java.net")
                || i.startsWith("java.sql") || i.startsWith("javax.sql") || i.startsWith("org.springframework")
                || i.startsWith("com.fasterxml") || i.matches("com\\.rpsbattle\\.(app|agent|bot|match|replay|rules)\\..*"));
        assertThat(source("game")).doesNotContain("System.out", "System.in", "ProcessBuilder", "Docker");
    }

    @Test
    void rulesDoNotDependOnUiOrNetwork() throws Exception {
        for (String folder : List.of("rule", "rules")) {
            assertThat(imports(folder)).noneMatch(i -> i.startsWith("java.io") || i.startsWith("java.net")
                    || i.matches("com\\.rpsbattle\\.(app|agent|bot|match|replay)\\..*"));
            assertThat(source(folder)).doesNotContain("System.out", "System.in");
        }
    }

    @Test
    void engineDoesNotDependOnClassicRules() throws Exception {
        assertThat(imports("game/engine")).noneMatch(i -> i.startsWith("com.rpsbattle.rules.classic"));
        assertThat(source("game/engine")).doesNotContain("PieceType.ROCK", "PieceType.PAPER", "PieceType.SCISSORS");
    }

    @Test
    void botsNeverImportOrConstructGameEngine() throws Exception {
        assertThat(imports("bot")).noneMatch(i -> i.startsWith("com.rpsbattle.game.engine"));
        assertThat(source("bot")).doesNotContain("new GameEngine(");
    }

    @Test
    void randomnessMustBeInjectedOrExplicitlySeeded() throws Exception {
        String all = source("");
        assertThat(Pattern.compile("new\\s+(?:Random|SplittableRandom)\\s*\\(\\s*\\)").matcher(all).find()).isFalse();
        assertThat(all).doesNotContain("Math.random(", "RandomGenerator.getDefault(", "ThreadLocalRandom.current(");
    }

    @Test
    void matchDoesNotDependOnConcreteAgentsOrConsole() throws Exception {
        assertThat(imports("match")).noneMatch(i -> i.startsWith("java.io") || i.startsWith("com.rpsbattle.bot")
                || i.endsWith("ConsoleHumanAgent") || i.endsWith("RandomBotAgent") || i.startsWith("com.rpsbattle.app"));
    }
}
