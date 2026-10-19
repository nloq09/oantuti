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
    @Test void upgradeV23AddsPersonApplicationsWithoutCreatingBotsOrExecutionRoster()throws Exception{
        var schema="timeline_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("23").load();old.migrate();UUID owner=UUID.randomUUID(),cup=UUID.randomUUID(),application=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()){user(c,owner,"timelineowner");sql.executeUpdate("insert into tournaments(id,owner_user_id,name,format,status) values('"+cup+"','"+owner+"','Timeline','ROUND_ROBIN','REGISTRATION')");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()){
            String insert="insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,status,registered_at,rules_accepted_at) values('%s','%s','%s','Player','CPP','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
            sql.executeUpdate(insert.formatted(application,cup,owner));assertThatThrownBy(()->sql.executeUpdate(insert.formatted(UUID.randomUUID(),cup,owner))).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.executeUpdate("update tournament_timeline_registrations set status='FORGED' where id='"+application+"'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.executeUpdate("update tournament_timeline_registrations set intended_language='SHELL' where id='"+application+"'")).isInstanceOf(SQLException.class);
            try(var rows=sql.executeQuery("select (select count(*) from bots),(select count(*) from tournament_participants),(select count(*) from tournament_registrations)")){rows.next();assertThat(rows.getLong(1)).isZero();assertThat(rows.getLong(2)).isZero();assertThat(rows.getLong(3)).isZero();}
        }
    }
    @Test void upgradeV20BackfillsBotPolicyWithoutChangingTournamentData()throws Exception {
        var schema="policy_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("20").load();old.migrate();var owner=UUID.randomUUID();var cup=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()){user(c,owner,"policyowner");sql.executeUpdate("insert into tournaments(id,owner_user_id,name,format,status) values('"+cup+"','"+owner+"','Legacy','ROUND_ROBIN','DRAFT')");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()){
            try(var rows=sql.executeQuery("select policy_json::jsonb->>'submissionMode',jsonb_array_length(policy_json::jsonb->'allowedLanguages') from tournament_bot_policies where tournament_id='"+cup+"'")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("PRE_UPLOADED_ONLY");assertThat(rows.getInt(2)).isEqualTo(3);}
            assertThatThrownBy(()->sql.executeUpdate("update tournament_bot_policies set submission_mode='UNSAFE' where tournament_id='"+cup+"'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.executeUpdate("update tournament_bot_policies set allowed_languages='RUST' where tournament_id='"+cup+"'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.executeUpdate("update tournament_bot_policies set allowed_languages=NULL where tournament_id='"+cup+"'")).isInstanceOf(SQLException.class);
        }
    }
    @Test void upgradeV19PreservesStructureAndAllowsSwissAndTeams()throws Exception{
        var schema="swissteam_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("19").load();old.migrate();var owner=UUID.randomUUID();var cup=UUID.randomUUID();String structure="{\"version\":1,\"maxConcurrentMatches\":1,\"tieBreakOrder\":[\"POINTS\"],\"rounds\":[],\"bracket\":[],\"matches\":{},\"stages\":[]}";
        try(var c=connect(old)){user(c,owner,"swissteamowner");try(var insert=c.prepareStatement("insert into tournaments(id,owner_user_id,name,format,status,structure_json) values(?,?,'Legacy group','GROUP_KNOCKOUT','DRAFT',?)")){insert.setObject(1,cup);insert.setObject(2,owner);insert.setString(3,structure);insert.executeUpdate();}}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(5);latest.validate();try(var c=connect(latest);var sql=c.createStatement()){for(String format:List.of("SWISS","TEAM_ROUND_ROBIN"))sql.executeUpdate("insert into tournaments(id,owner_user_id,name,format,status) values('"+UUID.randomUUID()+"','"+owner+"','New format','"+format+"','DRAFT')");try(var rows=sql.executeQuery("select format,structure_json from tournaments where id='"+cup+"'")){rows.next();assertThat(rows.getString(1)).isEqualTo("GROUP_KNOCKOUT");assertThat(rows.getString(2)).isEqualTo(structure);}assertThatThrownBy(()->sql.executeUpdate("update tournaments set format='UNKNOWN' where id='"+cup+"'")).isInstanceOf(SQLException.class);}
    }
    @Test void upgradeV18AllowsGroupFinalRematchAndRetainsSeriesUniqueness()throws Exception{
        var schema="groups_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("18").load();old.migrate();
        var owner=UUID.randomUUID();var other=UUID.randomUUID();var cup=UUID.randomUUID();var a=UUID.randomUUID();var b=UUID.randomUUID();var game=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()){user(c,owner,"groupowner");user(c,other,"groupother");sql.executeUpdate("insert into tournaments(id,owner_user_id,name,format,status) values('"+cup+"','"+owner+"','Old cup','ROUND_ROBIN','DRAFT')");for(var pair:List.of(Map.entry(a,owner),Map.entry(b,other)))sql.executeUpdate("insert into tournament_participants(id,tournament_id,owner_user_id,human_user_id,display_name,seed) values('"+pair.getKey()+"','"+cup+"','"+pair.getValue()+"','"+pair.getValue()+"','Human',1)");sql.executeUpdate("insert into tournament_matches(id,tournament_id,series_id,participant_a_id,participant_b_id,status) values('"+game+"','"+cup+"','"+game+"','"+a+"','"+b+"','FINISHED')");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(6);
        try(var c=connect(latest);var sql=c.createStatement()){sql.executeUpdate("update tournaments set format='GROUP_KNOCKOUT' where id='"+cup+"'");var rematch=UUID.randomUUID();sql.executeUpdate("insert into tournament_matches(id,tournament_id,series_id,participant_a_id,participant_b_id,status) values('"+rematch+"','"+cup+"','"+rematch+"','"+a+"','"+b+"','PENDING')");String duplicate="insert into tournament_matches(id,tournament_id,series_id,game_number,participant_a_id,participant_b_id,status) values('";String tail="','"+cup+"','"+rematch+"',2,'"+a+"','"+b+"','PENDING')";sql.executeUpdate(duplicate+UUID.randomUUID()+tail);assertThatThrownBy(()->sql.executeUpdate(duplicate+UUID.randomUUID()+tail)).isInstanceOf(SQLException.class);try(var rows=sql.executeQuery("select status from tournament_matches where id='"+game+"'")){rows.next();assertThat(rows.getString(1)).isEqualTo("FINISHED");}}
    }
    @Test void upgradeV17PreservesBo1AndConstrainsAdditionalSeriesGames()throws Exception{
        var schema="series_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("17").load();old.migrate();var owner=UUID.randomUUID();var other=UUID.randomUUID();var cup=UUID.randomUUID();var a=UUID.randomUUID();var b=UUID.randomUUID();var game=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()){user(c,owner,"seriesowner");user(c,other,"seriesother");sql.executeUpdate("insert into tournaments(id,owner_user_id,name,format,status) values('"+cup+"','"+owner+"','Legacy BO1','ROUND_ROBIN','DRAFT')");for(var pair:List.of(Map.entry(a,owner),Map.entry(b,other)))sql.executeUpdate("insert into tournament_participants(id,tournament_id,owner_user_id,human_user_id,display_name,seed) values('"+pair.getKey()+"','"+cup+"','"+pair.getValue()+"','"+pair.getValue()+"','Human',1)");sql.executeUpdate("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values('"+game+"','"+cup+"','"+a+"','"+b+"','FINISHED')");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(7);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()){try(var rows=sql.executeQuery("select series_id,game_number,status from tournament_matches where id='"+game+"'")){rows.next();assertThat(rows.getObject(1).toString()).isEqualTo(game.toString());assertThat(rows.getInt(2)).isEqualTo(1);assertThat(rows.getString(3)).isEqualTo("FINISHED");}String insert="insert into tournament_matches(id,tournament_id,series_id,game_number,participant_a_id,participant_b_id,status) values('";String tail="','"+cup+"','"+game+"',2,'"+a+"','"+b+"','PENDING')";sql.executeUpdate(insert+UUID.randomUUID()+tail);assertThatThrownBy(()->sql.executeUpdate(insert+UUID.randomUUID()+tail)).isInstanceOf(SQLException.class);assertThatThrownBy(()->sql.executeUpdate(insert+UUID.randomUUID()+tail.replace(",2,",",6,"))).isInstanceOf(SQLException.class);}
    }
    @Test void upgradeV16KeepsLegacyBotRowsAndConstrainsHumanIdentity()throws Exception{
        var schema="human_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("16").load();old.migrate();var owner=UUID.randomUUID();var tournament=UUID.randomUUID();
        try(var c=connect(old)){user(c,owner,"humanmigration");try(var s=c.prepareStatement("insert into tournaments(id,owner_user_id,name,format,status,revision,visibility,created_at,status_changed_at) values(?,?,?,'ROUND_ROBIN','DRAFT',0,'PUBLIC',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){s.setObject(1,tournament);s.setObject(2,owner);s.setString(3,"Human migration fixture");s.executeUpdate();}}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(8);latest.validate();
        try(var c=connect(latest);var s=c.prepareStatement("insert into tournament_registrations(id,tournament_id,user_id,human_user_id,display_name,status,registered_at) values(?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)")){s.setObject(1,UUID.randomUUID());s.setObject(2,tournament);s.setObject(3,owner);s.setObject(4,owner);s.setString(5,"Human");assertThat(s.executeUpdate()).isEqualTo(1);s.setObject(1,UUID.randomUUID());assertThatThrownBy(s::executeUpdate).isInstanceOf(SQLException.class);}
        try(var c=connect(latest);var s=c.createStatement();var rows=s.executeQuery("select count(*) from users")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
    }
    @Test void upgradeV15PreservesAccountsAndAddsAppendOnlyOperationMetadata()throws Exception{
        var schema="operations_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("15").load();old.migrate();var owner=UUID.randomUUID();
        try(var c=connect(old)){user(c,owner,"adminoperationfixture");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(9);latest.validate();
        try(var c=connect(latest);var statement=c.prepareStatement("insert into admin_operation_events(id,actor_user_id,actor_role,resource_type,resource_id,action,result,http_status,reason,occurred_at) values(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")){
            statement.setObject(1,UUID.randomUUID());statement.setObject(2,owner);statement.setString(3,"ADMIN");statement.setString(4,"USER");statement.setString(5,owner.toString());statement.setString(6,"POST_REQUEST");statement.setString(7,"FAILURE");statement.setInt(8,403);statement.setString(9,"HTTP_403");assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        try(var c=connect(latest);var statement=c.createStatement();var rows=statement.executeQuery("select count(*) from users")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);}
    }
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withLabel("rps.stage", "stage4-group1-test");

    private Flyway fresh() {
        var flyway = configured("case_" + UUID.randomUUID().toString().replace("-", ""));
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(24);
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
        assertThat(flyway.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(24);
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
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(15);latest.validate();
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
    @Test void upgradeV10BackfillsApprovedRegistrationsAndEnforcesOwnerAndCapacityConstraints() throws Exception {
        var schema="registration_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("10").load();old.migrate();
        var owner=UUID.randomUUID();var other=UUID.randomUUID();var bot=UUID.randomUUID();var cup=UUID.randomUUID();var participant=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()) {
            user(c,owner,"registered_owner");user(c,other,"other_owner");
            sql.executeUpdate("INSERT INTO bots(id,owner_user_id,name,language,source_path) VALUES('"+bot+"','"+owner+"','Legacy','PYTHON','main.py')");
            sql.executeUpdate("INSERT INTO tournaments(id,owner_user_id,name,format,status) VALUES('"+cup+"','"+owner+"','Legacy Cup','ROUND_ROBIN','READY')");
            sql.executeUpdate("INSERT INTO tournament_participants(id,tournament_id,bot_id,owner_user_id,display_name,seed) VALUES('"+participant+"','"+cup+"','"+bot+"','"+owner+"','Legacy',42)");
        }
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(14);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()) {
            try(var rows=sql.executeQuery("SELECT r.id,r.user_id,r.bot_id,r.bot_version_id,r.status,r.approved_at,t.check_in_required,t.max_participants FROM tournament_registrations r JOIN tournaments t ON t.id=r.tournament_id")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getObject(1,UUID.class)).isEqualTo(participant);assertThat(rows.getObject(2,UUID.class)).isEqualTo(owner);
                assertThat(rows.getObject(3,UUID.class)).isEqualTo(bot);assertThat(rows.getObject(4,UUID.class)).isEqualTo(bot);assertThat(rows.getString(5)).isEqualTo("APPROVED");assertThat(rows.getTimestamp(6)).isNotNull();assertThat(rows.getBoolean(7)).isFalse();assertThat(rows.getInt(8)).isEqualTo(10);
            }
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournament_registrations SET user_id='"+other+"' WHERE id='"+participant+"'"))
                .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournaments SET max_participants=1 WHERE id='"+cup+"'"))
                .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23514"));
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournament_registrations SET status='INVALID' WHERE id='"+participant+"'"))
                .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23514"));
        }
    }
    @Test void upgradeV11KeepsRegistrationsAndAddsSeedingAndHashConstraints() throws Exception {
        var schema="locks_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("11").load();old.migrate();
        var owner=UUID.randomUUID();var bot=UUID.randomUUID();var cup=UUID.randomUUID();var entry=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()) {
            user(c,owner,"lock_owner");sql.executeUpdate("INSERT INTO bots(id,owner_user_id,name,language,source_path) VALUES('"+bot+"','"+owner+"','Bot','PYTHON','main.py')");
            sql.executeUpdate("INSERT INTO tournaments(id,owner_user_id,name,format,status) VALUES('"+cup+"','"+owner+"','Cup','ROUND_ROBIN','REGISTRATION')");
            sql.executeUpdate("INSERT INTO tournament_registrations(id,tournament_id,user_id,bot_id,bot_version_id,display_name,status,registered_at) VALUES('"+entry+"','"+cup+"','"+owner+"','"+bot+"','"+bot+"','Bot','PENDING',CURRENT_TIMESTAMP)");
        }
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(13);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()) {
            try(var rows=sql.executeQuery("SELECT r.bot_id,r.artifact_hash,r.locked_at,t.seeding_strategy FROM tournament_registrations r JOIN tournaments t ON t.id=r.tournament_id")){assertThat(rows.next()).isTrue();assertThat(rows.getObject(1,UUID.class)).isEqualTo(bot);assertThat(rows.getString(2)).isNull();assertThat(rows.getTimestamp(3)).isNull();assertThat(rows.getString(4)).isEqualTo("RANDOM");}
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournament_registrations SET artifact_hash='"+"a".repeat(64)+"',locked_at=CURRENT_TIMESTAMP WHERE id='"+entry+"'")) .isInstanceOf(SQLException.class);
            sql.executeUpdate("UPDATE tournament_registrations SET source_hash='"+"a".repeat(64)+"',artifact_hash='"+"a".repeat(64)+"',locked_at=CURRENT_TIMESTAMP WHERE id='"+entry+"'");
            assertThatThrownBy(()->sql.executeUpdate("UPDATE tournaments SET seeding_strategy='INVALID' WHERE id='"+cup+"'")) .isInstanceOf(SQLException.class);
        }
    }
    @Test void upgradeV12AllowsKnockoutAnd32BotsWithoutChangingExistingDataOrNotNull() throws Exception {
        var schema="bracket_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("12").load();old.migrate();
        var owner=UUID.randomUUID();var cup=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()) {user(c,owner,"bracket_owner");sql.executeUpdate("INSERT INTO tournaments(id,owner_user_id,name,format,status,seeding_seed) VALUES('"+cup+"','"+owner+"','Existing','ROUND_ROBIN','READY',9223372036854775807)");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(12);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()) {
            try(var rows=sql.executeQuery("SELECT name,format,status,seeding_seed,structure_json FROM tournaments WHERE id='"+cup+"'")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Existing");assertThat(rows.getString(2)).isEqualTo("ROUND_ROBIN");assertThat(rows.getString(3)).isEqualTo("READY");assertThat(rows.getLong(4)).isEqualTo(Long.MAX_VALUE);assertThat(rows.getString(5)).isNull();}
            sql.executeUpdate("UPDATE tournaments SET format='SINGLE_ELIMINATION',max_participants=32 WHERE id='"+cup+"'");
            for(var assignment:List.of("format='UNSUPPORTED'","max_participants=33","max_participants=1","format=NULL","max_participants=NULL"))assertThatThrownBy(()->sql.executeUpdate("UPDATE tournaments SET "+assignment+" WHERE id='"+cup+"'")).isInstanceOf(SQLException.class);
        }
    }
    @Test void upgradeV13PreservesTournamentAndAddsAuditSequenceAndActorConstraints() throws Exception {
        var schema="audit_"+UUID.randomUUID().toString().replace("-","");var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("13").load();old.migrate();var owner=UUID.randomUUID();var cup=UUID.randomUUID();
        try(var c=connect(old);var sql=c.createStatement()){user(c,owner,"audit_owner");sql.executeUpdate("INSERT INTO tournaments(id,owner_user_id,name,format,status) VALUES('"+cup+"','"+owner+"','Legacy','SINGLE_ELIMINATION','READY')");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(11);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement()){
            sql.executeUpdate("INSERT INTO tournament_audit_events(id,tournament_id,sequence_number,event_type,actor_user_id,occurred_at) VALUES('"+UUID.randomUUID()+"','"+cup+"',1,'CREATED','"+owner+"',CURRENT_TIMESTAMP)");
            assertThatThrownBy(()->sql.executeUpdate("INSERT INTO tournament_audit_events(id,tournament_id,sequence_number,event_type,occurred_at) VALUES('"+UUID.randomUUID()+"','"+cup+"',1,'DUPLICATE',CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.executeUpdate("INSERT INTO tournament_audit_events(id,tournament_id,sequence_number,event_type,actor_user_id,occurred_at) VALUES('"+UUID.randomUUID()+"','"+cup+"',2,'INVALID','"+UUID.randomUUID()+"',CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            try(var rows=sql.executeQuery("SELECT status,lock_version FROM tournaments WHERE id='"+cup+"'")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("READY");assertThat(rows.getLong(2)).isZero();}
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
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(16);
        try(var c=connect(latest);var sql=c.createStatement();var rows=sql.executeQuery("SELECT match_id,event_count,valid,sha256 FROM replays")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("legacy");assertThat(rows.getInt(2)).isEqualTo(3);assertThat(rows.getBoolean(3)).isFalse();assertThat(rows.getString(4)).isNull();
        }
    }
    @Test void upgradesExistingV6AccountsWithoutChangingOwnershipOrPassword() throws Exception {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().configuration(configured(schema).getConfiguration()).target("6").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(6);
        var id=UUID.randomUUID();try(var c=connect(old)){user(c,id,"existing_account");}
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(18);latest.validate();
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
        var latest=configured(schema);assertThat(latest.migrate().migrationsExecuted).isEqualTo(17);latest.validate();
        try(var c=connect(latest);var sql=c.createStatement();var rows=sql.executeQuery("SELECT room_id,winner_seat,turn_count FROM matches WHERE id='old_room'")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("old_room");assertThat(rows.getString(2)).isEqualTo("P1");assertThat(rows.getInt(3)).isEqualTo(9);
        }
    }

}
