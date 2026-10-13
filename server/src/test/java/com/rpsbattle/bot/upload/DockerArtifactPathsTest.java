package com.rpsbattle.bot.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DockerArtifactPathsTest {
    @TempDir Path root;
    @Test void daemonVolumeMappingMountsOnlyTheSourceFileAndNeverMetadataOrSiblingBots() {
        String id=UUID.randomUUID().toString();
        String source=DockerArtifactPaths.mappedSource(root,root.resolve(id).resolve("main.py"),"/var/lib/docker/volumes/bot-data/_data");
        assertThat(source).isEqualTo("/var/lib/docker/volumes/bot-data/_data/"+id+"/main.py");
        assertThatThrownBy(()->DockerArtifactPaths.mappedSource(root,root.resolve(id).resolve("metadata.json"),"/volume")).hasMessage("BOT_ARTIFACT_MISSING");
        assertThatThrownBy(()->DockerArtifactPaths.mappedSource(root,root.resolve("../../main.py"),"/volume")).hasMessage("BOT_ARTIFACT_MISSING");
        assertThatThrownBy(()->DockerArtifactPaths.mappedSource(root,root.resolve(id).resolve("nested/main.py"),"/volume")).hasMessage("BOT_ARTIFACT_MISSING");
    }
    @Test void bindModePreservesLocalPathAndRejectsPathsOutsideArtifactRoot() {
        var mapping=new DockerArtifactPaths("");var path=root.resolve(UUID.randomUUID().toString()).resolve("main.py");
        assertThat(mapping.source(root,path)).isEqualTo(path.toAbsolutePath().toString());
        assertThatThrownBy(()->mapping.source(root,root.resolve("../escape.py"))).hasMessage("BOT_ARTIFACT_MISSING");
        assertThatThrownBy(()->new DockerArtifactPaths("../volume")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void labelledCommandRetainsSandboxLimitsAndNoDockerSocketMount() {
        var labels=Map.of("rps.managed","true","rps.scope","deployment","rps.boot",UUID.randomUUID().toString(),"botId",UUID.randomUUID().toString(),"matchId",UUID.randomUUID().toString());
        var command=DockerBotProcess.command("/volume/main.py",BotLanguage.PYTHON,"rps-bot-test",labels);
        assertThat(command).contains("--network=none","--read-only","--user=65534:65534","--memory=384m","--pids-limit=64");
        assertThat(command.stream().filter("--mount"::equals)).hasSize(1);
        assertThat(String.join(" ",command)).doesNotContain("docker.sock","metadata.json");
        for(var label:labels.entrySet())assertThat(command).contains(label.getKey()+"="+label.getValue());
    }
}
