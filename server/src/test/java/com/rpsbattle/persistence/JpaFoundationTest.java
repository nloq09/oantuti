package com.rpsbattle.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JpaFoundationTest {
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Flyway flyway;
    @Autowired Environment environment;

    @Test void springBootInitializesJpaAfterAllFlywayMigrationsWithTestOnlyDatabase() throws Exception {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(flyway.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(15);
        flyway.validate();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        try(var connection=dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
    }
}
