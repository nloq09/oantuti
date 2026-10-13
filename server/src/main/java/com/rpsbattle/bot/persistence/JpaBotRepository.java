package com.rpsbattle.bot.persistence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
interface JpaBotRepository extends JpaRepository<BotEntity,UUID> {
    long countByStatusNot(com.rpsbattle.bot.ownership.BotMetadata.Status status);
    List<BotEntity> findByOwnerUserIdAndStatusNotOrderByCreatedAtDesc(UUID owner,com.rpsbattle.bot.ownership.BotMetadata.Status status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from BotEntity b where b.id=:id")
    Optional<BotEntity> locked(@Param("id") UUID id);
}
