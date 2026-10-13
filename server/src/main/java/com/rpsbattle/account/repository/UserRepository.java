package com.rpsbattle.account.repository;

import com.rpsbattle.account.domain.*;
import java.util.Optional;
import java.util.List;

public interface UserRepository {
    Optional<User> findById(UserId id);
    Optional<User> findByIdForUpdate(UserId id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    User save(User user);
    Page findAll(int page,int size);
    long count();
    record Page(List<User> users,long total,int page,int size) {}
}
