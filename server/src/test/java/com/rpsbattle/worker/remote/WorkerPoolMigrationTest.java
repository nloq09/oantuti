package com.rpsbattle.worker.remote;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class WorkerPoolMigrationTest {
    protected DriverManagerDataSource database(){return new DriverManagerDataSource("jdbc:h2:mem:pool-upgrade-"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
    @Test void upgradeCopiesPoliciesAndRetryFencesWithoutDeletingHistoricalData(){
        var ds=database();Flyway.configure().dataSource(ds).target("42").load().migrate();var db=new JdbcTemplate(ds);
        UUID actor=UUID.randomUUID(),cup=UUID.randomUUID(),group=UUID.randomUUID(),match=UUID.randomUUID(),job=UUID.randomUUID();
        db.update("insert into users(id,username,email,password_hash,display_name) values(?,'upgrade','upgrade@example.test','fixture','Upgrade')",actor);
        db.update("insert into tournaments(id,owner_user_id,name,format,status) values(?,?,'Upgrade','ROUND_ROBIN','RUNNING')",cup,actor);
        var seats=new ArrayList<UUID>();for(int i=0;i<2;i++){UUID bot=UUID.randomUUID(),seat=UUID.randomUUID();db.update("insert into bots(id,owner_user_id,name,language,source_path,status) values(?,?,'Fixture','PYTHON','fixture','READY')",bot,actor);db.update("insert into tournament_participants(id,tournament_id,bot_id,owner_user_id,display_name,seed) values(?,?,?,?,'Fixture',?)",seat,cup,bot,actor,i);seats.add(seat);}
        db.update("insert into tournament_matches(id,tournament_id,participant_a_id,participant_b_id,status) values(?,?,?,?,'PENDING')",match,cup,seats.get(0),seats.get(1));
        db.update("insert into worker_groups(id,name,enabled) values(?,'Historical lab',false)",group);
        db.update("insert into tournament_worker_policies values(?,'REMOTE_REQUIRED',?,'TRUSTED',8,7)",cup,group);
        db.update("insert into match_execution_jobs values(?,?,?,'QUEUED','{}',?,'PYTHON','PYTHON','1','classic-online-v1','TRUSTED',CURRENT_TIMESTAMP)",job,match,cup,"a".repeat(64));
        db.update("insert into remote_job_routes values(?,?,3,2)",job,group);
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(db.queryForMap("select mode,minimum_trust,max_matches,revision from worker_pool_policies where tournament_id=?",cup)).containsEntry("mode","REMOTE_REQUIRED").containsEntry("minimum_trust","TRUSTED").containsEntry("max_matches",8).containsEntry("revision",7L);
        assertThat(db.queryForMap("select match_attempt,retry_floor from worker_pool_routes where job_id=?",job)).containsEntry("match_attempt",3).containsEntry("retry_floor",2);
        assertThat(db.queryForObject("select group_id from remote_job_routes where job_id=?",UUID.class,job)).isEqualTo(group);
        assertThat(db.queryForObject("select group_id from tournament_worker_policies where tournament_id=?",UUID.class,cup)).isEqualTo(group);
        assertThat(db.queryForObject("select enabled from worker_groups where id=?",Boolean.class,group)).isFalse();
        // New pool policies and routes are independent of the retained historical records.
        db.update("update worker_pool_policies set mode='AUTO',revision=8 where tournament_id=?",cup);
        assertThat(db.queryForObject("select revision from tournament_worker_policies where tournament_id=?",Long.class,cup)).isEqualTo(7);
        Flyway.configure().dataSource(ds).load().validate();
    }
}
