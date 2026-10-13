package com.rpsbattle.auth;

import com.rpsbattle.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker=true)
class UserRepositoryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine")
            .withLabel("rps.stage","stage4-group2-test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource dataSource;

    @Test void registrationCommitsJpaAccountAndBcryptHashToRealPostgres() throws Exception {
        String username="pg_"+UUID.randomUUID().toString().substring(0,8),password="Postgres test password";
        var created=registration.register(username,username+"@example.com",password,"Postgres player");
        var loaded=users.findById(created.id()).orElseThrow();
        assertThat(loaded.username()).isEqualTo(username);
        assertThat(passwords.matches(password,loaded.passwordHash())).isTrue();
        try(var connection=dataSource.getConnection();var statement=connection.prepareStatement("SELECT username,password_hash FROM users WHERE id=?")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            statement.setObject(1,created.id().value());
            try(var rows=statement.executeQuery()) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(username);
                assertThat(rows.getString(2)).startsWith("$2a$12$").isNotEqualTo(password);
            }
        }
    }
}
