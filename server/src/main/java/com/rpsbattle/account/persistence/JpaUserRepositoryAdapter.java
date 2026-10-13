package com.rpsbattle.account.persistence;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JpaUserRepositoryAdapter implements UserRepository {
    private final JpaUserRepository repository;
    public JpaUserRepositoryAdapter(JpaUserRepository repository) { this.repository=repository; }
    public Optional<User> findById(UserId id) { return repository.findById(id.value()).map(UserEntity::toDomain); }
    public Optional<User> findByUsername(String name) { return repository.findByUsername(normalize(name)).map(UserEntity::toDomain); }
    public Optional<User> findByEmail(String email) { return repository.findByEmail(normalize(email)).map(UserEntity::toDomain); }
    public boolean existsByUsername(String name) { return repository.existsByUsername(normalize(name)); }
    public boolean existsByEmail(String email) { return repository.existsByEmail(normalize(email)); }
    public User save(User user) { return repository.saveAndFlush(UserEntity.from(user)).toDomain(); }
    private String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
}
