package com.rpsbattle.auth;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class UserRepositoryTest {
    @Autowired UserRepository users;
    @Test void domainAndEntityAreSeparateAndRepositoryNormalizesLookups() {
        var name="repo_"+UUID.randomUUID().toString().substring(0,8);var now=Instant.now();
        var user=new User(UserId.random(),name,name+"@example.com","hashed-fixture","Repo",UserRole.USER,AccountStatus.ACTIVE,now,now,null);
        var saved=users.save(user);
        assertThat(users.findById(saved.id())).isPresent();
        assertThat(users.findByUsername("  "+name.toUpperCase()+"  ").orElseThrow().id()).isEqualTo(user.id());
        assertThat(users.findByEmail(name.toUpperCase()+"@EXAMPLE.COM").orElseThrow().id()).isEqualTo(user.id());
        assertThat(users.existsByUsername(name)).isTrue();assertThat(users.existsByEmail(user.email())).isTrue();
        assertThat(users.findById(UserId.random())).isEmpty();
        assertThat(user.toString()).doesNotContain("hashed-fixture",user.email());
    }
}
