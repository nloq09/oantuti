package com.rpsbattle.admin;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AccountPrincipal;
import com.rpsbattle.bot.ownership.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @Testcontainers(disabledWithoutDocker=true)
class AdminDirectoryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");}
    @Autowired UserRepository users;@Autowired BotRepository bots;@Autowired AdminDirectoryService directory;@Autowired PlatformTransactionManager transactions;
    User user(UserRole role){var id=UserId.random();String name="pgadmin_"+UUID.randomUUID().toString().substring(0,8);var now=Instant.now();return users.save(new User(id,name,name+"@example.invalid","fixture-not-for-login","Postgres Fixture",role,AccountStatus.ACTIVE,now,now,null,0));}
    UsernamePasswordAuthenticationToken auth(User user){return UsernamePasswordAuthenticationToken.authenticated(AccountPrincipal.from(user),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role())));}
    @Test void accountModerationBindsPostgresTimestampsAndPersistsAuditWithDateFilters(){
        var admin=auth(user(UserRole.ADMIN));var target=user(UserRole.USER);
        directory.userStatus(admin,target.id().value(),AccountStatus.DISABLED,"PostgreSQL moderation");
        assertThat(directory.user(admin,target.id().value()).get("status")).isEqualTo("DISABLED");
        assertThat((List<?>)directory.user(admin,target.id().value()).get("audit")).hasSize(1);
        var day=LocalDate.now(ZoneOffset.UTC).toString();var page=directory.list(admin,"users",Map.of("q",target.username(),"from",day,"to",day),0,20);assertThat(page.get("total")).isEqualTo(1L);
    }
    @Test void botModerationBindsPostgresUpdateAndAuditTimesAndCanEnableAgain(){
        var owner=user(UserRole.USER);var admin=auth(user(UserRole.ADMIN));var id=UUID.randomUUID();var now=Instant.now();
        new TransactionTemplate(transactions).execute(status->bots.create(new BotMetadata(id,owner.id().value(),"Postgres bot",com.rpsbattle.bot.upload.BotLanguage.PYTHON,"fixture/source",BotMetadata.Status.UPLOADED,now,now)));
        assertThat(directory.botStatus(admin,id,"disable","PostgreSQL quarantine").get("admin_disabled")).isEqualTo(true);
        var result=directory.botStatus(admin,id,"enable","PostgreSQL review");assertThat(result.get("admin_disabled")).isEqualTo(false);assertThat((List<?>)result.get("audit")).hasSize(2);
    }
}
