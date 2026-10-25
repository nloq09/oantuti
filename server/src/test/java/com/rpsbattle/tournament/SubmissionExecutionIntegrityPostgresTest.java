package com.rpsbattle.tournament;

import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SubmissionExecutionIntegrityPostgresTest extends SubmissionExecutionIntegrityApiTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
  properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
  properties.add("spring.datasource.username",POSTGRES::getUsername);
  properties.add("spring.datasource.password",POSTGRES::getPassword);
  properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
 }
}
