package com.rpsbattle.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.api.dto.OnlineSnapshot;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.history.domain.MatchMetadata;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Room listeners only enqueue captured values. File and DB I/O happen outside room locks. */
@Component
public class ReplayArchiveStore {
    public record Archive(MatchLog log, OnlineSnapshot snapshot) {}
    private record Pending(MatchMetadata match, Archive archive) {}
    private final ConcurrentMap<String,Pending> pending=new ConcurrentHashMap<>();
    private final ReplayRepository repository;
    private final ObjectMapper json;
    private final GameStateMapper mapper;
    private final Path root;
    private final ReplayService replay=new ReplayService();
    public ReplayArchiveStore(ReplayRepository repository,ObjectMapper json,GameStateMapper mapper,
            @Value("${rps.replays.directory:data/replays}") String directory) {
        this.repository=repository;this.json=json;this.mapper=mapper;this.root=Path.of(directory).toAbsolutePath().normalize();
    }
    public void capture(MatchMetadata match,MatchLog log,OnlineSnapshot snapshot) {
        pending.putIfAbsent(match.id(),new Pending(match,new Archive(log,snapshot)));
    }
    /** Synchronous remote projection; caller includes repository writes in its receipt transaction. */
    public void persistRemote(MatchMetadata match,MatchLog log,OnlineSnapshot snapshot) {
        try {
            validate(new Archive(log,snapshot));var id=UUID.fromString(match.id());Files.createDirectories(root);
            Path target=safePath(id+".json"),temporary=Files.createTempFile(root,"remote-replay-",".tmp");
            try {
                replay.write(temporary,log);var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(temporary.toFile());
                payload.put("archiveVersion",1);payload.put("archiveId",match.id());payload.set("snapshot",json.valueToTree(snapshot));
                byte[] bytes=json.writeValueAsBytes(payload);if(bytes.length>16*1024*1024)throw new IllegalArgumentException("Oversized replay");
                Files.write(temporary,bytes);try(var file=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)){file.force(true);}
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                repository.save(new ReplayMetadata(id,match.id(),id+".json",log.events().size(),true,match.finishedAt(),digest(bytes)),match);
            }finally{Files.deleteIfExists(temporary);}
        }catch(Exception e){throw new IllegalStateException("Remote replay persistence unavailable",e);}
    }
    public synchronized void flush() {
        RuntimeException failed=null;
        for(var item:pending.entrySet()) {
            try {
                var p=item.getValue();var id=UUID.fromString(item.getKey());
                var filename=id+".json";
                Files.createDirectories(root);
                var target=safePath(filename);
                var temporary=Files.createTempFile(root,"replay-",".tmp");
                try {
                    validate(p.archive());
                    replay.write(temporary,p.archive().log());
                    var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(temporary.toFile());
                    payload.put("archiveVersion",1);payload.put("archiveId",item.getKey());
                    payload.set("snapshot",json.valueToTree(p.archive().snapshot()));
                    json.writeValue(temporary.toFile(),payload);
                    var digest=digest(Files.readAllBytes(temporary));
                    Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    repository.save(new ReplayMetadata(id,item.getKey(),filename,p.archive().log().events().size(),true,Instant.now(),digest),p.match());
                    pending.remove(item.getKey(),p);
                } finally {Files.deleteIfExists(temporary);}
            } catch(Exception failure) {if(failed==null) failed=new IllegalStateException("Replay persistence unavailable",failure);}
        }
        if(failed!=null) throw failed;
    }
    public Archive load(String id) {
        try {flush();} catch(RuntimeException failure) {throw new AuthException("REPLAY_PERSISTENCE_UNAVAILABLE",503);}
        var metadata=repository.find(id).orElseThrow(()->new AuthException("REPLAY_NOT_READY",400));
        if(!metadata.valid()) throw new AuthException("REPLAY_INVALID",422);
        try {
            if(!metadata.filePath().equals(metadata.matchId()+".json")) throw new IllegalArgumentException("Unexpected replay filename");
            var path=safePath(metadata.filePath());
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {
                repository.invalidate(metadata.matchId());throw new AuthException("REPLAY_FILE_MISSING",404);
            }
            if(Files.size(path)>16*1024*1024) throw new IllegalArgumentException("Oversized replay");
            var bytes=Files.readAllBytes(path);
            if(!digest(bytes).equals(metadata.sha256())) throw new IllegalArgumentException("Replay digest mismatch");
            var payload=json.readTree(bytes);
            if(payload.path("archiveVersion").asInt()!=1 || !payload.path("archiveId").asText().equals(metadata.matchId())) throw new IllegalArgumentException("Replay identity mismatch");
            var archive=new Archive(replay.read(bytes),json.treeToValue(payload.get("snapshot"),OnlineSnapshot.class));
            if(archive.log().events().size()!=metadata.eventCount()) throw new IllegalArgumentException("Event count mismatch");
            validate(archive);return archive;
        } catch(AuthException failure) {throw failure;}
        catch(Exception failure) {repository.invalidate(metadata.matchId());throw new AuthException("REPLAY_INVALID",422);}
    }
    /** Metadata and file presence only; explicit validation additionally checks hash and game facts. */
    public String availability(String id){
        var stored=repository.find(id);if(stored.isEmpty())return "MISSING";var metadata=stored.get();
        try{if(!metadata.filePath().equals(metadata.matchId()+".json"))return "INVALID";if(!Files.isRegularFile(safePath(metadata.filePath()),LinkOption.NOFOLLOW_LINKS))return "MISSING";return metadata.valid()?"VALID":"INVALID";}
        catch(Exception failure){return "INVALID";}
    }
    private void validate(Archive archive) {
        var actual=mapper.toDto(replay.replay(archive.log()),List.of(),List.of());
        if(!actual.equals(archive.snapshot().gameState()) || !"FINISHED".equals(archive.snapshot().status())) throw new IllegalArgumentException("Final replay snapshot mismatch");
    }
    private Path safePath(String filename) throws java.io.IOException {
        var path=root.resolve(filename).normalize();
        if(!path.getParent().equals(root) || Files.isSymbolicLink(root) || Files.isSymbolicLink(path)) throw new IllegalArgumentException("Invalid replay path");
        if(Files.exists(root) && !root.toRealPath().equals(root)) throw new IllegalArgumentException("Replay root redirected");
        return path;
    }
    private static String digest(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    @Scheduled(fixedDelayString="${rps.replays.flush-millis:500}") public void synchronize() {
        if(pending.isEmpty()) return;
        try {flush();} catch(RuntimeException failure) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Replay persistence unavailable; {} archives retained for retry",pending.size());}
    }
    @PreDestroy void shutdown() {synchronize();}
}
