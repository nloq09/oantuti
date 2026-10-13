package com.rpsbattle.tournament.persistence;
import com.rpsbattle.history.MatchHistoryRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
@Repository
public class TournamentRepositoryAdapter implements TournamentRepository {
    private final JpaTournamentRepository tournaments;
    private final JpaTournamentParticipantRepository participants;
    private final JpaTournamentMatchRepository matches;
    private final MatchHistoryRepository history;
    @PersistenceContext EntityManager entities;
    public TournamentRepositoryAdapter(JpaTournamentRepository tournaments,JpaTournamentParticipantRepository participants,JpaTournamentMatchRepository matches,MatchHistoryRepository history) {this.tournaments=tournaments;this.participants=participants;this.matches=matches;this.history=history;}
    @Transactional public void save(TournamentMetadata metadata) {
        var stored=entities.find(TournamentEntity.class,metadata.id(),LockModeType.PESSIMISTIC_WRITE);
        if(stored!=null && stored.revision>metadata.revision()) return;
        if(stored!=null && !stored.ownerUserId.equals(metadata.ownerUserId())) throw new IllegalStateException("Tournament owner cannot be changed");
        if(stored==null) {stored=new TournamentEntity();stored.copy(metadata);entities.persist(stored);}else stored.copy(metadata);
        entities.flush();
        var roster=participants.findByTournamentIdOrderById(metadata.id()).stream().collect(Collectors.toMap(p->p.id,p->p));
        for(var p:metadata.participants()) {
            var old=roster.remove(p.id());
            if(old==null) entities.persist(new TournamentParticipantEntity(metadata.id(),p));
            else if(!old.botId.equals(p.botId()) || !old.ownerUserId.equals(p.ownerUserId())) throw new IllegalStateException("Participant ownership cannot be changed");
        }
        entities.flush();
        var entries=matches.findByTournamentIdOrderById(metadata.id()).stream().collect(Collectors.toMap(m->m.id,m->m));
        for(var e:metadata.matches()) {
            if(e.history()!=null) history.save(e.history());
            var old=entries.remove(e.id());
            if(old==null) {old=new TournamentMatchEntity();old.copy(metadata.id(),e);entities.persist(old);}else old.copy(metadata.id(),e);
        }
        entries.values().forEach(entities::remove);entities.flush();
        roster.values().forEach(entities::remove);entities.flush();
    }
    @Transactional(readOnly=true) public List<TournamentMetadata> all() {
        // PRIVATE is intentionally unsupported in this version; never publish existing private rows.
        return tournaments.findByVisibilityOrderByCreatedAtAsc("PUBLIC").stream().map(t->{
            var roster=participants.findByTournamentIdOrderById(t.id).stream().map(TournamentParticipantEntity::domain).toList();
            var schedule=matches.findByTournamentIdOrderById(t.id).stream().map(m->{
                String room=m.historyId==null?null:history.find(m.historyId).map(h->h.roomId()).orElse(null);
                return new TournamentMetadata.Entry(m.id,m.a,m.b,m.historyId,room,m.status,m.winner,m.result,m.failure,m.remainingA==null?0:m.remainingA,m.remainingB==null?0:m.remainingB,m.startedAt,m.finishedAt,null);
            }).toList();
            return new TournamentMetadata(t.id,t.ownerUserId,t.name,t.format,t.status,t.revision,t.createdAt,t.startedAt,t.finishedAt,roster,schedule);
        }).toList();
    }
}
