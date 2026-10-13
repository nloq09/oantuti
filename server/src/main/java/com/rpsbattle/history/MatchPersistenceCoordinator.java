package com.rpsbattle.history;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.online.*;
import jakarta.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** Capture immutable metadata under the room monitor; DB I/O runs outside it. */
@Component
public class MatchPersistenceCoordinator {
    private final OnlineMatchService online;
    private final MatchRegistry registry;
    private final MatchHistoryRepository repository;
    private final ConcurrentMap<String,MatchMetadata> observed=new ConcurrentHashMap<>(), pending=new ConcurrentHashMap<>();
    public MatchPersistenceCoordinator(OnlineMatchService online,MatchRegistry registry,MatchHistoryRepository repository) {this.online=online;this.registry=registry;this.repository=repository;}
    @PostConstruct void subscribe() {online.listen(update->capture(update.match()));}
    public void capture(OnlineMatch room) {
        synchronized(room) {
            var metadata=MatchMetadataMapper.capture(room);
            var previous=observed.put(metadata.id(),metadata);
            if(!metadata.equals(previous)) pending.put(metadata.id(),metadata);
        }
    }
    public synchronized void flush() {
        RuntimeException failed=null;
        for(var entry:pending.entrySet()) {
            try {
                repository.save(entry.getValue());
                // A newer capture arriving during DB I/O remains queued for the next flush.
                pending.remove(entry.getKey(),entry.getValue());
            } catch(RuntimeException failure) {if(failed==null) failed=failure;}
        }
        if(failed!=null) throw failed;
    }
    public void flushForRead() {
        try {flush();} catch(RuntimeException failure) {throw new AuthException("MATCH_PERSISTENCE_UNAVAILABLE",503);}
    }
    public MatchMetadata latest(String id) {return id==null?null:observed.get(id);}
    @Scheduled(fixedDelayString="${rps.history.flush-millis:500}") public void synchronize() {
        for(var room:registry.getActiveMatches()) capture(room);
        try {flush();} catch(RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Match metadata persistence unavailable; {} snapshots retained for retry",pending.size());
        }
    }
    @PreDestroy void shutdown() {synchronize();}
}
