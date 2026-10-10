package com.rpsbattle.bot.upload;

import com.rpsbattle.bot.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Only the uploaded artifact is mounted; never runs it in a host interpreter/compiler.
 */
public final class DockerBotProcess implements BotProcess {
    private final String name = "rps-bot-" + UUID.randomUUID();
    private final LocalProcessRunner transport;
    private boolean first = true;

    public DockerBotProcess(Path artifact, BotLanguage language) {
        transport = new LocalProcessRunner(command(artifact, language, name));
    }

    public static List<String> command(Path artifact, BotLanguage language, String name) {
        var command = new ArrayList<>(List.of("docker", "run", "--rm", "--pull=never", "-i", "--name", name,
                "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges:true",
                "--user=65534:65534", "--pids-limit=64", "--memory=384m", "--memory-swap=384m", "--cpus=0.5",
                "--ulimit=nofile=64:64", "--tmpfs=/tmp:rw,nosuid,nodev," + (language == BotLanguage.CPP ? "exec" : "noexec") + ",size=64m,mode=1777",
                "--log-driver=none", "--mount", "type=bind,source=" + artifact.toAbsolutePath() + ",target=/bot/" + language.filename + ",readonly",
                "--workdir=/tmp", "--entrypoint=/bin/sh", language.image, "-c", language.command));
        return List.copyOf(command);
    }

    public void start() {
        transport.start();
    }

    public String exchange(String request, Duration timeout) {
        try {
            return transport.exchange(request, first ? Duration.ofSeconds(25) : timeout);
        } finally {
            first = false;
        }
    }

    public boolean isAlive() {
        return transport.isAlive();
    }

    public void close() {
        transport.close();
        try {
            var cleanup = new ProcessBuilder("docker", "rm", "--force", name).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!cleanup.waitFor(3, TimeUnit.SECONDS)) cleanup.destroyForcibly();
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
