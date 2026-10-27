package com.rpsbattle.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    interface PublicName { UUID getId(); String getDisplayName(); }
    @Query("select u.id as id, u.displayName as displayName from UserEntity u where u.id in :ids")
    List<PublicName> displayNames(@Param("ids") Collection<UUID> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
