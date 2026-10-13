package com.rpsbattle.bot.ownership;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.upload.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="rps.bots.enabled=false")
@Testcontainers(disabledWithoutDocker=true)
class BotRepositoryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine").withLabel("rps.stage","stage4-group4-test");
    @TempDir static Path root;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("rps.bots.directory",root::toString);
    }
    @Autowired OwnedBotService owned;
    @Autowired LegacyBotImportService legacy;
    @Autowired BotUploadService artifacts;
    @Autowired UserRepository users;
    @Autowired BotRepository bots;
    @Autowired DataSource database;
    @Autowired PlatformTransactionManager transactions;
    User user() {
        var id=UserId.random();var name="pg_"+id.value().toString().substring(0,8);var now=Instant.now();
        return users.save(new User(id,name,name+"@example.com","unused hash","Player",UserRole.USER,AccountStatus.ACTIVE,now,now,null));
    }
    MockMultipartFile file() {return new MockMultipartFile("file","bot.py","text/plain","print(1)".getBytes());}
    @Test void metadataCommitsToPostgresAndSoftDeletePreservesArtifactAndOwner() throws Exception {
        var user=user();var auth=AuthService.sessionAuthentication(user);var bot=owned.upload(auth,"Persistent",file());
        var stored=owned.execution(auth,bot.id().toString());
        try(var connection=database.getConnection();var query=connection.prepareStatement("SELECT owner_user_id,name,source_path,status FROM bots WHERE id=?")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");query.setObject(1,bot.id());
            try(var result=query.executeQuery()) {assertThat(result.next()).isTrue();assertThat(result.getObject(1)).isEqualTo(user.id().value());assertThat(result.getString(2)).isEqualTo("Persistent");assertThat(result.getString(3)).isEqualTo(bot.id()+"/main.py");assertThat(result.getString(4)).isEqualTo("UPLOADED");}
        }
        owned.rename(auth,bot.id().toString(),"Renamed");assertThat(bots.find(bot.id()).orElseThrow().name()).isEqualTo("Renamed");
        owned.delete(auth,bot.id().toString());assertThat(owned.list(auth)).isEmpty();
        assertThat(bots.find(bot.id()).orElseThrow().ownerUserId()).isEqualTo(user.id().value());
        assertThat(Files.isRegularFile(artifacts.source(stored))).isTrue();
    }
    @Test void twoAccountsClaimingSameLegacyArtifactCannotOverwriteOwner() throws Exception {
        var a=user();var b=user();var upload=artifacts.upload("Shared legacy",file());
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(var user:List.of(a,b)) results.add(pool.submit(()-> {
                ready.countDown();start.await();
                try {legacy.claim(AuthService.sessionAuthentication(user),upload.id(),upload.token());return true;}
                catch(com.rpsbattle.auth.AuthException | org.springframework.dao.DataIntegrityViolationException expected) {return false;}
            }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            int successes=0;for(var result:results) if(result.get(10,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);assertThat(bots.find(UUID.fromString(upload.id()))).isPresent();
            assertThat(Files.isRegularFile(artifacts.source(upload))).isTrue();
        }
    }
    @Test void rolledBackNewUploadIsCompensatedAndMissingLegacySourceIsNotImported() throws Exception {
        var user=user();var auth=AuthService.sessionAuthentication(user);
        String[] id={null};
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            id[0]=owned.upload(auth,"Rollback",file()).id().toString();throw new IllegalStateException("rollback test");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(bots.find(UUID.fromString(id[0]))).isEmpty();assertThat(Files.exists(root.resolve(id[0]))).isFalse();
        var upload=artifacts.upload("Missing source",file());Files.delete(artifacts.source(upload));
        assertThatThrownBy(()->legacy.claim(auth,upload.id(),upload.token())).isInstanceOf(com.rpsbattle.auth.AuthException.class).hasMessage("BOT_ARTIFACT_MISSING");
        assertThat(bots.find(UUID.fromString(upload.id()))).isEmpty();assertThat(Files.exists(root.resolve(upload.id()).resolve("metadata.json"))).isTrue();
    }
}
