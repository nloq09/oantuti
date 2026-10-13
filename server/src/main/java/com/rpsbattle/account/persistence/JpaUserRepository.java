package com.rpsbattle.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
