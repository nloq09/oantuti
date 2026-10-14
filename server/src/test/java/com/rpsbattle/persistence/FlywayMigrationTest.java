package com.rpsbattle.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withLabel("rps.stage", "stage4-group1-test");

    private Flyway fresh() {
        var flyway = configured("case_" + UUID.randomUUID().toString().replace("-", ""));
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(10);
        return flyway;
    }
    private Flyway configured(String schema) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).locations("classpath:db/migration").cleanDisabled(true)
                .validateMigrationNaming(true).load();
    }
    private Connection connect(Flyway flyway) throws SQLException {
        var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(flyway.getConfiguration().getSchemas()[0]);
        return connection;
    }
    private void user(Connection c, UUID id, String name) throws SQLException {
        try (var statement = c.prepareStatement("INSERT INTO users (id,username,email,password_hash,display_name) VALUES (?,?,?,?,?)")) {
            statement.setObject(1,id);statement.setString(2,name);statement.setString(3,name+"@example.com");
            statement.setString(4,"$2a$12$schema.fixture.hash");statement.setString(5,name);statement.executeUpdate();
        }
    }
    @Test void migratesEmptyPostgresAndRepeatedStartupDoesNotReapply() throws Exception {
        var flyway = fresh();flyway.validate();
        assertThat(flyway.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(10);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var c=connect(flyway);var statement=c.createStatement()) {
            for (var table:List.of("users","bots","matches","match_participants","tournaments","tournament_participants","tournament_matches","replays"))
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
        }
    }
    @Test void upgradeV9PreservesExistingStatusesAndAddsLifecycleChecksWithoutDroppingNotNull() throws Exception {
        var schema="lifecycle_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("9").load();old.migrate();
        var owner=UUID.randomUUID();var ids=new LinkedHashMap<String,UUID>();
        try(var c=connect(old)) {
            user(c,owner,"lifecycle_owner");
            for(var status:List.of("DRAFT","READY","RUNNING","FINISHED","CANCELLED","RECOVERY_REQUIRED")) {
                var id=UUID.randomUUID();ids.put(status,id);
                try(var statement=c.prepareStatement("INSERT INTO tournaments(id,owner_user_id,name,format,status,created_at) VALUES(?,?,?,'ROUND_ROBIN',?,'2026-10-01T00:00:00Z')")) {
                    statement.setObject(1,id);statement.setObject(2,owner);statement.setString(3,status);statement.setString(4,status);statement.executeUpdate();
                }
            }
        }
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();
        assertThat(latest.info().applied()).filteredOn(info->info.getVersion()!=null && info.getVersion().toString().equals("10")).allMatch(info->info.getChecksum()!=null).hasSize(1);
        try(var c=connect(latest);var sql=c.createStatement();var rows=sql.executeQuery("SELECT id,owner_user_id,status,status_changed_at FROM tournaments")) {
            int count=0;while(rows.next()) {assertThat(rows.getObject(1,UUID.class)).isEqualTo(ids.get(rows.getString(3)));assertThat(rows.getObject(2,UUID.class)).isEqualTo(owner);assertThat(rows.getTimestamp(4).toInstant()).isEqualTo(java.time.Instant.parse("2026-10-01T00:00:00Z"));count++;}assertThat(count).isEqualTo(6);
        }
        try(var c=connect(latest);var sql=c.createStatement()) {
            for(var status:List.of("REGISTRATION","CHECK_IN","PAUSED"))sql.executeUpdate("INSERT INTO tournaments(id,owner_user_id,name,format,status) VALUES('"+UUID.randomUUID()+"','"+owner+"','New','ROUND_ROBIN','"+status+"')");
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournaments SET status='INVALID' WHERE id='"+ids.get("DRAFT")+"'"))
                .isInstanceOf(SQLException.class).satisfies(error->assertThat(((SQLException)error).getSQLState()).isEqualTo("23514"));
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournaments SET status=NULL WHERE id='"+ids.get("DRAFT")+"'"))
                .isInstanceOf(SQLException.class).satisfies(error->assertThat(((SQLException)error).getSQLState()).isEqualTo("23502"));
        }
    }
    @Test void enforcesUniqueNormalizedAccountsAndForeignKeyOwnership() throws Exception {
        var flyway=fresh();
        try(var c=connect(flyway)) {
            user(c,UUID.randomUUID(),"alice");
            assertThatThrownBy(()->user(c,UUID.randomUUID(),"alice")).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23505"));
            assertThatThrownBy(()->user(c,UUID.randomUUID(),"Alice")).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23514"));
            try(var statement=c.createStatement()) {
                assertThatThrownBy(()->statement.executeUpdate("INSERT INTO bots (id,owner_user_id,name,language,source_path) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','Bot','PYTHON','bots/main.py')"))
                        .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
            }
        }
    }
    @Test void metadataSurvivesNewConnectionsAndMigrationStartup() throws Exception {
        var flyway=fresh();var owner=UUID.randomUUID();var bot=UUID.randomUUID();var tournament=UUID.randomUUID();
        try(var c=connect(flyway);var statement=c.createStatement()) {
            user(c,owner,"persisted");
            statement.executeUpdate("INSERT INTO bots (id,owner_user_id,name,language,source_path) VALUES ('"+bot+"','"+owner+"','Alpha','PYTHON','bots/alpha.py')");
            statement.executeUpdate("INSERT INTO matches (id,mode,status,player1_user_id,winner_user_id,outcome,turn_count,rule_set) VALUES ('archived','HUMAN_VS_BOT','FINISHED','"+owner+"','"+owner+"','WIN',42,'Classic')");
            statement.executeUpdate("INSERT INTO tournaments (id,owner_user_id,name,format,status) VALUES ('"+tournament+"','"+owner+"','Cup','ROUND_ROBIN','DRAFT')");
            statement.executeUpdate("INSERT INTO replays (id,match_id,file_path,event_count,valid) VALUES ('"+UUID.randomUUID()+"','archived','replays/archived.json',100,TRUE)");
        }
        var reopened=configured(flyway.getConfiguration().getSchemas()[0]);
        reopened.validate();assertThat(reopened.migrate().migrationsExecuted).isZero();
        try(var c=connect(reopened);var statement=c.createStatement()) {
            for(var table:List.of("users","bots","matches","tournaments","replays"))
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
            assertThatThrownBy(()->statement.executeUpdate("DELETE FROM users WHERE id='"+owner+"'"))
                    .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
        }
    }
    @Test void rejectsModifiedMigrationHistoryAndDisablesClean() throws Exception {
        var flyway=fresh();
        assertThatThrownBy(flyway::clean).hasMessageContaining("cleanDisabled");
        try(var c=connect(flyway);var statement=c.createStatement()) {
            statement.executeUpdate("UPDATE flyway_schema_history SET checksum=0 WHERE version='1'");
        }
        assertThatThrownBy(flyway::validate).hasMessageContaining("checksum mismatch");
    }
    @Test void upgradeV8PreservesReplayMetadataButInvalidatesUnverifiedFiles() throws Exception {
        var schema="upgrade_replay_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("8").load();old.migrate();
        try(var c=connect(old);var sql=c.createStatement()) {
            sql.executeUpdate("INSERT INTO matches(id,mode,status,rule_set) VALUES('legacy','HUMAN_VS_HUMAN','FINISHED','classic')");
            sql.executeUpdate("INSERT INTO replays(id,match_id,file_path,event_count,valid) VALUES('"+UUID.randomUUID()+"','legacy','legacy.json',3,TRUE)");
        }
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
        try(var c=connect(latest);var sql=c.createStatement();var rows=sql.executeQuery("SELECT match_id,event_count,valid,sha256 FROM replays")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("legacy");assertThat(rows.getInt(2)).isEqualTo(3);assertThat(rows.getBoolean(3)).isFalse();assertThat(rows.getString(4)).isNull();
        }
    }
    @Test void upgradesExistingV6AccountsWithoutChangingOwnershipOrPassword() throws Exception {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("6").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(6);
        var id=UUID.randomUUID();try(var c=connect(old)){user(c,id,"existing_account");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);latest.validate();
        try(var c=connect(latest);var statement=c.createStatement();var rows=statement.executeQuery("SELECT id,username,password_hash,auth_version FROM users")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getObject(1,UUID.class)).isEqualTo(id);
            assertThat(rows.getString(2)).isEqualTo("existing_account");
            assertThat(rows.getString(3)).isEqualTo("$2a$12$schema.fixture.hash");assertThat(rows.getLong(4)).isZero();
        }
    }
    @Test void upgradeV7BackfillsRoomAndWinnerSeatWithoutLosingMatchData() throws Exception {
        String schema="upgrade_match_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).locations("classpath:db/migration").target("7").cleanDisabled(true).load();
        old.migrate();var id=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()) {
            user(c,id,"history_owner");
            sql.executeUpdate("INSERT INTO matches(id,mode,status,player1_user_id,winner_user_id,outcome,turn_count,rule_set) VALUES('old_room','HUMAN_VS_BOT','FINISHED','"+id+"','"+id+"','WIN',9,'classic-online-v1')");
        }
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(3);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement();var rows=sql.executeQuery("SELECT room_id,winner_seat,turn_count FROM matches WHERE id='old_room'")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("old_room");assertThat(rows.getString(2)).isEqualTo("P1");assertThat(rows.getInt(3)).isEqualTo(9);
        }
    }

}
