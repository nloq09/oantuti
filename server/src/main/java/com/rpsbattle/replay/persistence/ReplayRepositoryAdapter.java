package com.rpsbattle.replay.persistence;
import com.rpsbattle.replay.*;
import com.rpsbattle.history.*;
import com.rpsbattle.history.domain.MatchMetadata;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Repository
public class ReplayRepositoryAdapter implements ReplayRepository {
    @PersistenceContext EntityManager entities;
    private final MatchHistoryRepository history;
    public ReplayRepositoryAdapter(MatchHistoryRepository history) {this.history=history;}
    @Transactional public void save(ReplayMetadata replay, MatchMetadata match) {
        history.save(match); // Same transaction: the FK never races the history writer.
        var existing=entities.find(ReplayEntity.class,replay.id());
        if(existing==null) entities.persist(new ReplayEntity(replay));
        else {existing.valid=replay.valid();existing.sha256=replay.sha256();existing.eventCount=replay.eventCount();}
        entities.flush();
    }
    @Transactional(readOnly=true) public Optional<ReplayMetadata> find(String id) {
        var direct=entities.createQuery("select r from ReplayEntity r where r.matchId=:id",ReplayEntity.class).setParameter("id",id).getResultList();
        if(!direct.isEmpty()) return Optional.of(direct.getFirst().domain());
        // Pick the newest match, even if it has no replay; never return an older reused room's replay.
        var matches=entities.createQuery("select m.id from MatchEntity m where m.roomId=:id order by m.createdAt desc, m.id",String.class).setParameter("id",id).setMaxResults(1).getResultList();
        if(matches.isEmpty()) return Optional.empty();
        return entities.createQuery("select r from ReplayEntity r where r.matchId=:id",ReplayEntity.class).setParameter("id",matches.getFirst()).getResultStream().findFirst().map(ReplayEntity::domain);
    }
    @Transactional public void invalidate(String id) {
        entities.createQuery("update ReplayEntity r set r.valid=false where r.matchId=:id").setParameter("id",id).executeUpdate();
    }
}
