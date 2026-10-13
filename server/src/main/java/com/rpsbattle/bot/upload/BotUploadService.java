package com.rpsbattle.bot.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.bot.BotAgent;
import com.rpsbattle.online.DemoRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class BotUploadService {
    private final Path root;
    private final ObjectMapper json;
    private final boolean enabled;
    public BotUploadService(ObjectMapper json, @Value("${rps.bots.directory:data/bots}") String directory,
                            @Value("${rps.bots.enabled:true}") boolean enabled) {
        this.json = json;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.enabled = enabled;
    }

    public synchronized Upload upload(String name, MultipartFile file) {
        BotLanguage language = BotLanguage.fromFilename(file.getOriginalFilename());
        if (name == null || name.isBlank() || name.length() > 60) throw new DemoRequestException("INVALID_BOT_NAME");
        long limit = language == BotLanguage.JAR ? 5 * 1024 * 1024 : 512 * 1024;
        if (file.isEmpty() || file.getSize() > limit) throw new DemoRequestException("BOT_FILE_SIZE");
        try {
            Files.createDirectories(root);
            try (var files = Files.list(root)) {
                if (files.count() >= 100) throw new DemoRequestException("BOT_STORAGE_FULL");
            }
            byte[] data = file.getBytes();
            if (data.length > limit) throw new DemoRequestException("BOT_FILE_SIZE");
            if (language != BotLanguage.JAR && (new String(data, StandardCharsets.UTF_8).indexOf('\0') >= 0))
                throw new DemoRequestException("INVALID_BOT_FILE");
            if (language == BotLanguage.JAR && (data.length < 4 || data[0] != 'P' || data[1] != 'K'))
                throw new DemoRequestException("INVALID_BOT_FILE");
            var result = new Upload(UUID.randomUUID().toString(), name.trim(), language, UUID.randomUUID().toString());
            Path directory = root.resolve(result.id());
            Files.createDirectory(directory);
            Files.write(directory.resolve(language.filename), data, StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("metadata.json"), json.writeValueAsString(result), StandardOpenOption.CREATE_NEW);
            return result;
        } catch (java.io.IOException error) {
            throw new DemoRequestException("BOT_STORAGE_ERROR");
        }
    }

    public Upload authorize(String id, String token) {
        Upload upload = stored(id);
        if (token == null || !upload.token().equals(token)) throw new DemoRequestException("BOT_ACCESS_DENIED");
        return upload;
    }

    public Upload stored(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new DemoRequestException("BOT_NOT_FOUND");
        try {
            Upload upload = json.readValue(Files.readString(root.resolve(id).resolve("metadata.json")), Upload.class);
            if (!id.equals(upload.id())) throw new DemoRequestException("BOT_NOT_FOUND");
            return upload;
        } catch (java.io.IOException error) {
            throw new DemoRequestException("BOT_NOT_FOUND");
        }
    }

    public Path source(Upload upload) {return root.resolve(upload.id()).resolve(upload.language().filename);}

    public void discardNew(Upload upload) {
        Path directory=root.resolve(upload.id()).normalize();
        if(!directory.getParent().equals(root) || !upload.id().matches("[a-f0-9-]{36}") || Files.isSymbolicLink(directory)) return;
        try {
            Files.deleteIfExists(directory.resolve(upload.language().filename));
            Files.deleteIfExists(directory.resolve("metadata.json"));
            Files.deleteIfExists(directory);
        } catch(java.io.IOException error) {
            org.slf4j.LoggerFactory.getLogger(BotUploadService.class).warn("Cannot compensate newly uploaded bot {}",upload.id());
        }
    }

    public BotAgent agent(String id, String token) {
        Upload upload = authorize(id, token);
        if (!available(upload.language())) throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE");
        return new BotAgent(new DockerBotProcess(root.resolve(id).resolve(upload.language().filename), upload.language()), Duration.ofSeconds(2));
    }

    public boolean available(BotLanguage language) {
        return enabled && probe(List.of("docker", "info", "--format", "{{.OSType}}"), "linux")
                && probe(List.of("docker", "image", "inspect", language.image, "--format", "{{.Id}}"), "sha256:");
    }

    private boolean probe(List<String> command, String expected) {
        try {
            var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && new String(process.getInputStream().readNBytes(512), StandardCharsets.UTF_8).trim().startsWith(expected);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    public Map<String, Object> capabilities() {
        var ready = new LinkedHashMap<String, Boolean>();
        for (var language : BotLanguage.values()) ready.put(language.name(), available(language));
        return Map.of("languages", ready, "sourceMaxBytes", 512 * 1024, "jarMaxBytes", 5 * 1024 * 1024);
    }

    public record Upload(String id, String name, BotLanguage language, String token) {
        @Override public String toString() {return "Upload[id="+id+", language="+language+", token=[redacted]]";}
    }
}
