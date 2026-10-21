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
    public synchronized Tournament create(String name, TournamentFormat format) {
        if (name == null || name.isBlank() || name.length()>80) throw new DemoRequestException("INVALID_TOURNAMENT_NAME");
        if (tournaments.values().stream().filter(t->!t.archived&&t.status!=TournamentStatus.FINISHED&&t.status!=TournamentStatus.CANCELLED).count()>=100) throw new DemoRequestException("TOURNAMENT_LIMIT");
        var t=new Tournament(UUID.randomUUID().toString(),name.trim(),Objects.requireNonNull(format),Instant.now());
        tournaments.put(t.id,t); return t;
    }
    public Optional<Tournament> findById(String id) { return Optional.ofNullable(tournaments.get(id)); }
    public Collection<Tournament> findAll() { return List.copyOf(tournaments.values()); }
    public void remove(String id) { tournaments.remove(id); }
    public void register(Tournament tournament) {
        if(tournaments.putIfAbsent(tournament.id,tournament)!=null) throw new DemoRequestException("DUPLICATE_TOURNAMENT");
    }
}
