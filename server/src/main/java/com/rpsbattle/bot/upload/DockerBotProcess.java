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
    private LocalProcessRunner transport;
    private final String source,scope,bootId,botId;
    private final BotLanguage language;
    private String matchId="standalone";
    private boolean closed;
    private boolean first = true;

    public DockerBotProcess(Path artifact, BotLanguage language) {
        this(artifact.toAbsolutePath().toString(),language,"standalone",UUID.randomUUID().toString(),"standalone");
    }
    public DockerBotProcess(String source,BotLanguage language,String scope,String bootId,String botId) {
        this.source=source;this.language=language;this.scope=scope;this.bootId=bootId;this.botId=botId;
    }
    public synchronized void bindMatch(String id) {
        if(transport!=null)throw new IllegalStateException("Bot already started");
        matchId=UUID.fromString(id).toString();
    }
    private synchronized LocalProcessRunner runner() {
        if(closed)throw new BotProtocolException("Bot process is closed");
        if(transport==null)transport=new LocalProcessRunner(command(source,language,name,Map.of("rps.managed","true","rps.scope",scope,"rps.boot",bootId,"botId",botId,"matchId",matchId)));
        return transport;
    }

    public static List<String> command(Path artifact, BotLanguage language, String name) {
        return command(artifact.toAbsolutePath().toString(),language,name,Map.of("rps.managed","true","rps.scope","standalone"));
    }
    public static List<String> command(String source,BotLanguage language,String name,Map<String,String> labels) {
        var command = new ArrayList<>(List.of("docker", "run", "--rm", "--pull=never", "-i", "--name", name,
                "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges:true",
                "--user=65534:65534", "--pids-limit=64", "--memory=384m", "--memory-swap=384m", "--cpus=0.5",
                "--ulimit=nofile=64:64", "--tmpfs=/tmp:rw,nosuid,nodev," + (language == BotLanguage.CPP ? "exec" : "noexec") + ",size=64m,mode=1777",
                "--log-driver=none", "--mount", "type=bind,source=" + source + ",target=/bot/" + language.filename + ",readonly",
                "--workdir=/tmp", "--entrypoint=/bin/sh", language.image, "-c", language.command));
        int image=command.indexOf(language.image);
        for(var entry:new TreeMap<>(labels).entrySet()) {command.add(image++,"--label");command.add(image++,entry.getKey()+"="+entry.getValue());}
        return List.copyOf(command);
    }

    public void start() {
        runner().start();
    }

    public String exchange(String request, Duration timeout) {
        try {
            return runner().exchange(request, first ? Duration.ofSeconds(25) : timeout);
        } finally {
            first = false;
        }
    }

    public synchronized boolean isAlive() {
        return transport!=null && transport.isAlive();
    }

    public void close() {
        LocalProcessRunner current;
        synchronized(this) {closed=true;current=transport;}
        if(current==null)return;
        current.close();
        try {
            var cleanup = new ProcessBuilder("docker", "rm", "--force", name).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!cleanup.waitFor(3, TimeUnit.SECONDS)) cleanup.destroyForcibly();
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
