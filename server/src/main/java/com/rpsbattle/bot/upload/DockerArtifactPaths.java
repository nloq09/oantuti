package com.rpsbattle.bot.upload;

import com.rpsbattle.online.DemoRequestException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** A sibling bot container sees daemon-host paths, not paths inside the server container. */
public final class DockerArtifactPaths {
    private final String volume;
    private String mountpoint;
    public DockerArtifactPaths(String volume) {
        if(volume!=null && !volume.isEmpty() && !volume.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid bot volume");
        this.volume=volume==null?"":volume;
    }
    public synchronized String source(Path root,Path artifact) {
        var base=root.toAbsolutePath().normalize();var file=artifact.toAbsolutePath().normalize();
        if(!file.startsWith(base) || Files.isSymbolicLink(base) || Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())) throw new DemoRequestException("BOT_ARTIFACT_MISSING");
        if(volume.isEmpty())return file.toString();
        if(mountpoint==null) {
            try {
                var process=new ProcessBuilder("docker","volume","inspect",volume,"--format","{{.Mountpoint}}").redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if(!process.waitFor(3,TimeUnit.SECONDS)) {process.destroyForcibly();throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE");}
                String path=new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8).strip();
                if(process.exitValue()!=0 || !path.startsWith("/") || path.contains(",") || path.contains("\n") || path.contains("..")) throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE");
                mountpoint=path.replaceAll("/+$","");
            } catch(java.io.IOException error) {throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE");}
            catch(InterruptedException error) {Thread.currentThread().interrupt();throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE");}
        }
        return mappedSource(base,file,mountpoint);
    }
    public static String mappedSource(Path root,Path file,String mountpoint) {
        var relative=root.relativize(file);
        if(relative.getNameCount()!=2 || !relative.getName(0).toString().matches("[a-f0-9-]{36}") ||
                !java.util.Set.of("main.py","main.cpp","Main.java","bot.jar").contains(relative.getName(1).toString())) throw new DemoRequestException("BOT_ARTIFACT_MISSING");
        return mountpoint+"/"+relative.toString().replace('\\','/');
    }
}
