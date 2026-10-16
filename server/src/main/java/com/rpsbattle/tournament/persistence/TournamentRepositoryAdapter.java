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
        if(stored!=null && stored.revision==metadata.revision() && stored.details!=null && metadata.details()!=null && !TournamentDetailsConverter.equivalent(stored.details,metadata.details()))throw new org.springframework.dao.OptimisticLockingFailureException("Conflicting tournament revision");
        if(stored!=null && !stored.ownerUserId.equals(metadata.ownerUserId())) throw new IllegalStateException("Tournament owner cannot be changed");
        if(stored==null) {stored=new TournamentEntity();stored.copy(metadata);entities.persist(stored);}else stored.copy(metadata);
        entities.flush();
        var roster=participants.findByTournamentIdOrderById(metadata.id()).stream().collect(Collectors.toMap(p->p.id,p->p));
        for(var existing:roster.values())existing.seedNumber=null;entities.flush();
        for(var p:metadata.participants()) {
            var old=roster.remove(p.id());
            if(old==null) entities.persist(new TournamentParticipantEntity(metadata.id(),p));
            else if(!Objects.equals(old.botId,p.botId()) || !old.ownerUserId.equals(p.ownerUserId())) throw new IllegalStateException("Participant ownership cannot be changed");
            else {old.artifactHash=p.artifactHash();old.seedNumber=p.seedNumber();old.manualSeed=p.manualSeed();}
        }
        entities.flush();
        var entries=matches.findByTournamentIdOrderById(metadata.id()).stream().collect(Collectors.toMap(m->m.id,m->m));
        for(var e:metadata.matches()) {
            if(e.history()!=null) history.save(e.history());
            var old=entries.remove(e.id());
            boolean fresh=old==null;if(fresh)old=new TournamentMatchEntity();old.copy(metadata.id(),e);
            var detail=metadata.details()==null?null:metadata.details().matches().get(e.id().toString());old.seriesId=detail==null||detail.seriesId()==null?e.id():UUID.fromString(detail.seriesId());old.gameNumber=detail==null?1:Math.max(1,detail.gameNumber());
            UUID rootId=old.seriesId;var root=metadata.matches().stream().filter(game->game.id().equals(rootId)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing series root"));
            if(!root.a().equals(e.a())||!root.b().equals(e.b()))throw new IllegalArgumentException("Series participants cannot change");
            if(fresh)entities.persist(old);
        }
        entries.values().forEach(entities::remove);entities.flush();
        roster.values().forEach(entities::remove);entities.flush();
        var registrations=entities.createQuery("select r from TournamentRegistrationEntity r where r.tournamentId=:id",TournamentRegistrationEntity.class).setParameter("id",metadata.id()).getResultList().stream().collect(Collectors.toMap(r->r.id,r->r));
        for(var registration:metadata.registrations()) {
            if(!registration.tournamentId().equals(metadata.id()))throw new IllegalArgumentException("Registration tournament mismatch");
            var existing=registrations.remove(registration.id());
            if(existing==null){existing=new TournamentRegistrationEntity();existing.copy(registration);entities.persist(existing);}
            else {if(!existing.userId.equals(registration.userId()) || !Objects.equals(existing.botId,registration.botId()) || !Objects.equals(existing.botVersionId,registration.botVersionId()))throw new IllegalStateException("Registration identity cannot change");existing.copy(registration);}
        }
        registrations.values().forEach(entities::remove);entities.flush();
        var previous=entities.createQuery("select a from TournamentAuditEntity a where a.tournamentId=:id",TournamentAuditEntity.class).setParameter("id",metadata.id()).getResultList().stream().collect(Collectors.toMap(a->a.id,a->a));
        var audit=metadata.details()==null||metadata.details().operations()==null?List.<com.rpsbattle.tournament.domain.TournamentAuditEvent>of():metadata.details().operations().audit();
        for(var event:audit){var old=previous.remove(event.id());if(old==null)entities.persist(new TournamentAuditEntity(metadata.id(),event));else if(old.sequence!=event.sequence()||!old.type.equals(event.type())||!Objects.equals(old.actor,event.actor())||!Objects.equals(old.target,event.target())||!Objects.equals(old.reason,event.reason())||!Objects.equals(old.before,event.beforeValue())||!Objects.equals(old.after,event.afterValue()))throw new IllegalStateException("Tournament audit is append-only");}
        if(!previous.isEmpty())throw new IllegalStateException("Tournament audit cannot be removed");entities.flush();
    }
    @Transactional(readOnly=true) public List<TournamentMetadata> all() {
        // PRIVATE is intentionally unsupported in this version; never publish existing private rows.
        return tournaments.findByVisibilityOrderByCreatedAtAsc("PUBLIC").stream().map(t->{
            var roster=participants.findByTournamentIdOrderById(t.id).stream().map(TournamentParticipantEntity::domain).toList();
            var schedule=matches.findByTournamentIdOrderById(t.id).stream().map(m->{
                String room=m.historyId==null?null:history.find(m.historyId).map(h->h.roomId()).orElse(null);
                return new TournamentMetadata.Entry(m.id,m.a,m.b,m.historyId,room,m.status,m.winner,m.result,m.failure,m.remainingA==null?0:m.remainingA,m.remainingB==null?0:m.remainingB,m.startedAt,m.finishedAt,null);
            }).toList();
            return new TournamentMetadata(t.id,t.ownerUserId,t.name,t.format,t.status,t.revision,t.createdAt,t.startedAt,t.finishedAt,roster,schedule,t.statusChangedAt,t.checkInRequired,t.maxParticipants,entities.createQuery("select r from TournamentRegistrationEntity r where r.tournamentId=:id order by r.registeredAt,r.id",TournamentRegistrationEntity.class).setParameter("id",t.id).getResultList().stream().map(TournamentRegistrationEntity::domain).toList(),t.seedingStrategy,t.seedingSeed,t.details);
        }).toList();
    }
}
