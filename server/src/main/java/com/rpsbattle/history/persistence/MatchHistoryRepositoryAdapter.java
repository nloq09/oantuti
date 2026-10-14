package com.rpsbattle.history.persistence;
import com.rpsbattle.history.*;
import com.rpsbattle.history.domain.MatchMetadata;
import jakarta.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class MatchHistoryRepositoryAdapter implements MatchHistoryRepository {
    private final JpaMatchRepository matches;
    private final JpaParticipantRepository participants;
    @PersistenceContext EntityManager entities;
    private final MatchWriteLocks writeLocks=new MatchWriteLocks();
    public MatchHistoryRepositoryAdapter(JpaMatchRepository matches,JpaParticipantRepository participants) {this.matches=matches;this.participants=participants;}
    @Transactional public MatchMetadata save(MatchMetadata metadata) {
        try(var lock=writeLocks.acquire(metadata.id())) {return saveLocked(metadata);}
        catch(RuntimeException error){throw error;}
        catch(Exception error){throw new IllegalStateException("Cannot release match write lock",error);}
    }
    private MatchMetadata saveLocked(MatchMetadata metadata) {
        var stored=entities.find(MatchEntity.class,metadata.id(),LockModeType.PESSIMISTIC_WRITE);
        // Terminal results are immutable: repeated notifications cannot inflate stats or erase a result.
        if(stored!=null && stored.status.equals("FINISHED")) return domain(stored,participants.findByIdMatchIdOrderByIdSeat(stored.id));
        if(stored==null) {stored=new MatchEntity();stored.copy(metadata);entities.persist(stored);}
        else stored.copy(metadata);
        entities.flush();
        var current=participants.findByIdMatchIdOrderByIdSeat(metadata.id()).stream().collect(Collectors.toMap(p->p.id.seat,p->p));
        for(var p:metadata.participants()) {
            var previous=current.remove(p.seat());
            if(previous==null) entities.persist(new ParticipantEntity(metadata.id(),p));else previous.copy(p);
        }
        current.values().forEach(entities::remove);
        entities.flush();return metadata;
    }
    @Transactional(readOnly=true) public Optional<MatchMetadata> find(String id) {
        return matches.findById(id).map(m->domain(m,participants.findByIdMatchIdOrderByIdSeat(id)));
    }
    @Transactional(readOnly=true) public Page history(UUID owner,String filter,int page,int size) {
        Specification<MatchEntity> spec=(root,query,builder)-> {
            var owned=builder.or(builder.equal(root.get("player1UserId"),owner),builder.equal(root.get("player2UserId"),owner));
            var finished=builder.equal(root.get("status"),"FINISHED");
            var notSelf=builder.or(builder.isNull(root.get("player1UserId")),builder.isNull(root.get("player2UserId")),builder.notEqual(root.get("player1UserId"),root.get("player2UserId")));
            var selected=switch(filter) {
                case "win" -> builder.and(finished,notSelf,builder.equal(root.get("outcome"),"WIN"),builder.equal(root.get("winnerUserId"),owner));
                case "loss" -> builder.and(finished,notSelf,builder.equal(root.get("outcome"),"WIN"),builder.isNotNull(root.get("winnerSeat")),builder.or(builder.isNull(root.get("winnerUserId")),builder.notEqual(root.get("winnerUserId"),owner)));
                case "draw" -> builder.and(finished,notSelf,builder.equal(root.get("outcome"),"DRAW"));
                case "human", "bot" -> {
                    var bots=query.subquery(Integer.class);var seat=bots.from(ParticipantEntity.class);
                    bots.select(builder.literal(1)).where(builder.equal(seat.get("id").get("matchId"),root.get("id")),builder.notEqual(seat.get("controllerType"),MatchMetadata.ControllerType.HUMAN));
                    yield filter.equals("bot") ? builder.exists(bots) : builder.not(builder.exists(bots));
                }
                default -> builder.conjunction();
            };
            return builder.and(owned,selected);
        };
        var result=matches.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id"))));
        var roster=result.isEmpty()?Map.<String,List<ParticipantEntity>>of():participants.findByIdMatchIdIn(result.getContent().stream().map(m->m.id).toList()).stream().collect(Collectors.groupingBy(p->p.id.matchId));
        return new Page(result.stream().map(m->domain(m,roster.getOrDefault(m.id,List.of()))).toList(),result.getTotalElements(),page,size);
    }
    @Transactional(readOnly=true) public Totals totals(UUID owner) {
        var row=matches.aggregate(owner).getFirst();return new Totals(((Number)row[0]).longValue(),((Number)row[1]).longValue(),((Number)row[2]).longValue(),((Number)row[3]).longValue());
    }
    private MatchMetadata domain(MatchEntity m,List<ParticipantEntity> roster) {
        return new MatchMetadata(m.id,m.roomId==null?m.id:m.roomId,m.mode,m.status,m.player1UserId,m.player2UserId,m.winnerUserId,m.winnerSeat,m.outcome,m.turnCount,m.startedAt,m.finishedAt,m.durationMs,m.ruleSet,m.createdAt,
            roster.stream().sorted(Comparator.comparing(p->p.id.seat)).map(ParticipantEntity::domain).toList());
    }
}
