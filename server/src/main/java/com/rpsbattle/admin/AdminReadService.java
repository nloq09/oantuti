package com.rpsbattle.admin;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.MatchRegistry;
import com.rpsbattle.worker.WorkerManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Explicit metadata projections: never serialize JPA entities, source paths or credentials. */
@Service
public class AdminReadService {
    private final JdbcTemplate db;
    private final AccountAuthorizationService accounts;
    private final WorkerManager workers;
    private final MatchRegistry rooms;
    private final MeterRegistry metrics;
    public AdminReadService(JdbcTemplate db,AccountAuthorizationService accounts,WorkerManager workers,MatchRegistry rooms,MeterRegistry metrics) {
        this.db=db;this.accounts=accounts;this.workers=workers;this.rooms=rooms;this.metrics=metrics;
    }
    public Map<String,Object> dashboard(Authentication auth) {
        accounts.requireAdmin(auth);
        var result=new LinkedHashMap<String,Object>();
        result.put("totalUsers",count("select count(*) from users"));
        result.put("activeUsers",gauge("active_users"));
        result.put("enabledAccounts",count("select count(*) from users where status='ACTIVE'"));
        result.put("uploadedBots",count("select count(*) from bots where status<>'DELETED'"));
        result.put("activeMatches",rooms.getActiveMatches().stream().filter(m->!m.state().isFinished()&&m.infrastructureError()==null).count());
        result.put("runningTournaments",count("select count(*) from tournaments where status='RUNNING'"));
        result.put("openDisputes",null);result.put("disputesSupported",false);
        result.put("queuedMatches",workers.pending());result.put("workers",workers.status());
        result.put("systemErrors",count("select count(*) from tournament_matches where status='FAILED'"));
        result.put("webSocketSessions",gauge("websocket_sessions"));
        result.put("dockerContainers",null);
        result.put("checkedAt",Instant.now());
        result.put("activity",rows("select * from (select event_type,target_id,reason,occurred_at,tournament_id from tournament_audit_events union all select action || ':' || target_type,cast(target_id as varchar(160)),reason,occurred_at,cast(null as uuid) from admin_audit_events) events order by occurred_at desc limit 15"));
        return result;
    }
    private Double gauge(String name){var gauge=metrics.find(name).gauge();return gauge==null?null:gauge.value();}
    private long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    private List<Map<String,Object>> rows(String sql,Object...args){return db.query(sql,(rs,n)->{
        var row=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();
        for(int i=1;i<=meta.getColumnCount();i++){var value=rs.getObject(i);if(value instanceof java.sql.Timestamp timestamp)value=timestamp.toInstant();if(value instanceof java.time.OffsetDateTime time)value=time.toInstant();row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),value);}return row;
    },args);}
}
