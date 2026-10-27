package com.rpsbattle.tournament.registry;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.online.DemoRequestException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class TournamentRegistry {
    private final ConcurrentHashMap<String,Tournament> tournaments = new ConcurrentHashMap<>();
    private final Map<UUID,Set<Tournament>> related=new HashMap<>();
    private final Map<String,Set<UUID>> indexed=new HashMap<>();
    private long changeSequence;
    public synchronized long changeSequence(){return changeSequence;}
    public synchronized Set<Tournament> relatedTo(UUID user){return Set.copyOf(related.getOrDefault(user,Set.of()));}
    /** Called after committed mutations and restore; never takes a tournament lock while holding this index lock. */
    public void updateMemberships(Tournament t){
        var members=new HashSet<UUID>();synchronized(t){if(!t.archived){if(t.ownerUserId!=null)members.add(t.ownerUserId);t.registrations.stream().map(TournamentRegistration::userId).filter(Objects::nonNull).forEach(members::add);}}
        synchronized(this){if(tournaments.get(t.id)!=t)return;changeSequence++;var previous=indexed.getOrDefault(t.id,Set.of());if(previous.equals(members))return;
            for(var user:previous)if(!members.contains(user)){var cups=related.get(user);if(cups!=null){cups.remove(t);if(cups.isEmpty())related.remove(user);}}
            for(var user:members)if(!previous.contains(user))related.computeIfAbsent(user,key->new HashSet<>()).add(t);indexed.put(t.id,Set.copyOf(members));
        }
    }
    public synchronized Tournament create(String name, TournamentFormat format) {
        if (name == null || name.isBlank() || name.length()>80) throw new DemoRequestException("INVALID_TOURNAMENT_NAME");
        if (tournaments.values().stream().filter(t->!t.archived&&t.status!=TournamentStatus.FINISHED&&t.status!=TournamentStatus.CANCELLED).count()>=100) throw new DemoRequestException("TOURNAMENT_LIMIT");
        var t=new Tournament(UUID.randomUUID().toString(),name.trim(),Objects.requireNonNull(format),Instant.now());
        tournaments.put(t.id,t); return t;
    }
    public Optional<Tournament> findById(String id) { return Optional.ofNullable(tournaments.get(id)); }
    public Collection<Tournament> findAll() { return List.copyOf(tournaments.values()); }
    public synchronized void remove(String id) {var t=tournaments.remove(id);for(var user:indexed.getOrDefault(id,Set.of())){var cups=related.get(user);if(cups!=null){cups.remove(t);if(cups.isEmpty())related.remove(user);}}indexed.remove(id);changeSequence++;}
    public void register(Tournament tournament) {
        if(tournaments.putIfAbsent(tournament.id,tournament)!=null) throw new DemoRequestException("DUPLICATE_TOURNAMENT");
        updateMemberships(tournament);
    }
}
