package com.rpsbattle.tournament.persistence;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.upload.BotUploadService;
import com.rpsbattle.history.MatchPersistenceCoordinator;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import jakarta.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class TournamentPersistenceCoordinator {
    private final TournamentRepository repository;
    private final TournamentRegistry registry;
    private final TournamentService service;
    private final MatchPersistenceCoordinator history;
    private final BotUploadService artifacts;
    private final ConcurrentMap<UUID,TournamentMetadata> pending=new ConcurrentHashMap<>(),observed=new ConcurrentHashMap<>();
    public TournamentPersistenceCoordinator(TournamentRepository repository,TournamentRegistry registry,TournamentService service,MatchPersistenceCoordinator history,BotUploadService artifacts) {this.repository=repository;this.registry=registry;this.service=service;this.history=history;this.artifacts=artifacts;}
    @PostConstruct void initialize() {restore();service.listen(this::capture);}
    public TournamentMetadata snapshot(Tournament t) {
        synchronized(t) {
            if(t.ownerUserId==null) return null; // Unmanaged internal/demo fixtures have no account to assign.
            var roster=t.participants.stream().map(p->new TournamentMetadata.Participant(UUID.fromString(p.participantId()),UUID.fromString(p.botId()),p.ownerUserId(),p.displayName(),p.seed())).toList();
            var entries=t.matches.stream().map(m->new TournamentMetadata.Entry(UUID.fromString(m.id),UUID.fromString(m.participantA.participantId()),UUID.fromString(m.participantB.participantId()),m.historyMatchId,m.onlineMatchId,m.status,m.winnerParticipantId==null?null:UUID.fromString(m.winnerParticipantId),m.result,m.failure,m.remainingA,m.remainingB,m.startedAt,m.finishedAt,history.latest(m.historyMatchId))).toList();
            return new TournamentMetadata(UUID.fromString(t.id),t.ownerUserId,t.name,t.format,t.status,t.revision,t.createdAt,t.startedAt,t.finishedAt,roster,entries,t.statusChangedAt,t.checkInRequired,t.maxParticipants,List.copyOf(t.registrations));
        }
    }
    public void capture(Tournament t) {
        synchronized(t) {
            if(t.deferNotifications) return;
            var snapshot=snapshot(t);if(snapshot==null) return;
            var old=observed.put(snapshot.id(),snapshot);
            if(!snapshot.equals(old)) pending.put(snapshot.id(),snapshot);
        }
    }
    public synchronized void persistNow(Tournament t) {
        var snapshot=snapshot(t);if(snapshot==null) return;
        repository.save(snapshot);observed.put(snapshot.id(),snapshot);
        var queued=pending.get(snapshot.id());if(queued!=null && queued.revision()<=snapshot.revision()) pending.remove(snapshot.id(),queued);
    }
    public synchronized void flush() {
        RuntimeException error=null;
        for(var entry:pending.entrySet()) {
            try {repository.save(entry.getValue());pending.remove(entry.getKey(),entry.getValue());}
            catch(RuntimeException failure) {if(error==null) error=failure;}
        }
        if(error!=null) throw error;
    }
    public void flushForRead() {try {flush();} catch(RuntimeException error) {throw new AuthException("TOURNAMENT_PERSISTENCE_UNAVAILABLE",503);}}
    @Scheduled(fixedDelayString="${rps.tournament.flush-millis:500}") public void synchronize() {
        for(var t:registry.findAll()) capture(t);
        try {flush();} catch(RuntimeException error) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Tournament persistence unavailable; {} snapshots retained for retry",pending.size());}
    }
    /** DRAFT is editable again; live matches are deliberately never resumed automatically. */
    public void restore() {
        for(var data:repository.all()) {
            var t=new Tournament(data.id().toString(),data.name(),data.format(),data.createdAt());t.ownerUserId=data.ownerUserId();t.status=data.status();t.revision=data.revision();t.startedAt=data.startedAt();t.finishedAt=data.finishedAt();t.statusChangedAt=data.statusChangedAt();t.checkInRequired=data.checkInRequired();t.maxParticipants=data.maxParticipants();t.registrations.addAll(data.registrations());
            var byId=new HashMap<UUID,TournamentParticipant>();
            for(var p:data.participants()) {
                String token="";try {token=artifacts.stored(p.botId().toString()).token();} catch(com.rpsbattle.online.DemoRequestException missing) { /* Readable even if an artifact is unavailable. */ }
                var participant=new TournamentParticipant(p.id().toString(),p.botId().toString(),token,p.displayName(),p.seed(),p.ownerUserId());t.participants.add(participant);byId.put(p.id(),participant);
            }
            for(var e:data.matches()) {
                var match=new TournamentMatch(e.id().toString(),t.id,byId.get(e.a()),byId.get(e.b()));match.historyMatchId=e.historyId();match.onlineMatchId=e.roomId();match.status=e.status();match.winnerParticipantId=e.winner()==null?null:e.winner().toString();match.result=e.result();match.failure=e.failure();match.remainingA=e.remainingA();match.remainingB=e.remainingB();match.startedAt=e.startedAt();match.finishedAt=e.finishedAt();t.matches.add(match);
            }
            if(t.status==TournamentStatus.RUNNING || t.status==TournamentStatus.PAUSED || (t.status==TournamentStatus.READY && !t.matches.isEmpty())) {
                TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());t.revision++;
                for(var match:t.matches) if(match.status==TournamentMatch.Status.RUNNING) {match.status=TournamentMatch.Status.FAILED;match.failure="SERVER_RESTART";match.finishedAt=Instant.now();}
                repository.save(snapshot(t));
            }
            registry.register(t);var restored=snapshot(t);observed.put(restored.id(),restored);
        }
    }
    @PreDestroy void shutdown() {synchronize();}
}
