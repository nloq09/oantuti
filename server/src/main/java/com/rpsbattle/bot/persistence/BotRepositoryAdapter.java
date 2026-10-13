package com.rpsbattle.bot.persistence;
import com.rpsbattle.bot.ownership.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class BotRepositoryAdapter implements BotRepository {
    private final JpaBotRepository repository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    public BotRepositoryAdapter(JpaBotRepository repository) {this.repository=repository;}
    public Optional<BotMetadata> find(UUID id) {return repository.findById(id).map(BotEntity::domain);}
    public Optional<BotMetadata> lock(UUID id) {return repository.locked(id).map(BotEntity::domain);}
    public List<BotMetadata> owned(UUID owner) {return repository.findByOwnerUserIdAndStatusNotOrderByCreatedAtDesc(owner,BotMetadata.Status.DELETED).stream().map(BotEntity::domain).toList();}
    public BotMetadata create(BotMetadata bot) {entities.persist(new BotEntity(bot));entities.flush();return bot;}
    public BotMetadata save(BotMetadata bot) {return repository.saveAndFlush(new BotEntity(bot)).domain();}
    public long countActive(){return repository.countByStatusNot(BotMetadata.Status.DELETED);}
}
