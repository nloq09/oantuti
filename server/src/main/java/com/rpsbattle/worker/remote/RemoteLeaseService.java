package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every renewal/expiry/recovery shares the queue/receipt guard. No stale execution can commit twice. */
@Service
public class RemoteLeaseService {
    public static final long GRACE_SECONDS=30,UPLOAD_MARGIN_SECONDS=120;
    public record Renew(Long epoch,List<UUID> executions){}
    public record Recovery(UUID executionId,String operation){}
    public record Grant(UUID executionId,UUID jobId,String status,Instant leaseUntil){}
    public record Grants(List<Grant> executions,Instant serverNow){}
    private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;private final int attempts;
    public RemoteLeaseService(JdbcTemplate db,ObjectMapper json,Clock clock,@Value("${rps.worker.remote-max-attempts:3}")int attempts){if(attempts<1||attempts>10)throw new IllegalArgumentException("Invalid retry limit");this.db=db;this.json=json;this.clock=clock;this.attempts=attempts;}
    private void guard(){db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);}
    private static java.sql.Timestamp at(Instant value){return java.sql.Timestamp.from(value);}
    private static Instant instant(Object value){return value instanceof java.sql.Timestamp s?s.toInstant():((OffsetDateTime)value).toInstant();}
    private Map<String,Object> row(UUID worker,UUID execution){var rows=db.queryForList("select a.*,j.status as job_status,j.spec_json,j.spec_hash,j.minimum_trust,j.tournament_id from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.id=? and a.worker_id=?",execution,worker);if(rows.isEmpty())throw new AuthException("EXECUTION_NOT_ASSIGNED",404);WorkerScope.require(db,worker,(UUID)rows.getFirst().get("tournament_id"));return rows.getFirst();}
    private RemoteMatchQueue.Spec spec(Map<String,Object> row){String raw=row.get("spec_json").toString();if(!WorkerIdentityService.hash(raw).equals(row.get("spec_hash")))throw new AuthException("REMOTE_JOB_CORRUPT",409);try{return json.readValue(raw,RemoteMatchQueue.Spec.class);}catch(Exception e){throw new AuthException("REMOTE_JOB_CORRUPT",409);}}
    private boolean trusted(UUID worker,Map<String,Object> row){return db.queryForObject("select count(*) from workers where id=? and approval_status='APPROVED' and status<>'DISABLED' and trust_level in ('TRUSTED','COMMUNITY') and (?='COMMUNITY' or trust_level='TRUSTED')",Integer.class,worker,row.get("minimum_trust"))==1;}
    private boolean pending(Map<String,Object> row){return db.queryForObject("select count(*) from tournament_matches m join tournaments t on t.id=m.tournament_id where m.id=? and m.status='PENDING' and t.status not in ('CANCELLED','FINISHED')",Integer.class,row.get("match_id"))==1;}
    private Instant cap(Map<String,Object> row){return instant(row.get("started_at")==null?row.get("claimed_at"):row.get("started_at")).plusMillis(spec(row).matchHardTimeoutMs()).plusSeconds(UPLOAD_MARGIN_SECONDS);}
    private Grant grant(Map<String,Object> row,String status,Instant lease){return new Grant((UUID)row.get("id"),(UUID)row.get("job_id"),status,lease);}
    @Transactional
    public Grants renew(UUID worker,Renew body){
        if(body==null||body.epoch()==null||body.epoch()<1||body.executions()==null||body.executions().size()>16||body.executions().stream().anyMatch(Objects::isNull)||new HashSet<>(body.executions()).size()!=body.executions().size())throw new AuthException("INVALID_LEASE_RENEWAL",400);
        guard();if(db.queryForObject("select count(*) from workers where id=? and heartbeat_epoch=? and approval_status='APPROVED' and status<>'DISABLED' and last_heartbeat_at>?",Integer.class,worker,body.epoch(),at(clock.instant().minusSeconds(30)))!=1)throw new AuthException("STALE_WORKER_SESSION",409);
        var grants=new ArrayList<Grant>();Instant now=clock.instant();
        for(UUID execution:body.executions()){
            var row=row(worker,execution);Instant lease=instant(row.get("lease_until"));String status=row.get("status").toString();
            if(!Set.of("CLAIMED","RUNNING").contains(status)){grants.add(grant(row,status,lease));continue;}
            if(((Number)row.get("heartbeat_epoch")).longValue()!=body.epoch()||!"LEASED".equals(row.get("job_status"))||!trusted(worker,row)||!pending(row)||!now.isBefore(lease)||!now.isBefore(cap(row))){grants.add(grant(row,"LEASE_LOST",lease));continue;}
            Instant until=now.plusSeconds(30);if(until.isBefore(lease))until=lease;if(until.isAfter(cap(row)))until=cap(row);
            db.update("update match_execution_attempts set lease_until=? where id=?",at(until),execution);grants.add(grant(row,"ACTIVE",until));
        }
        return new Grants(grants,now);
    }
    @Transactional
    public Grant recover(UUID worker,Recovery body){
        if(body==null||body.executionId()==null||!Set.of("RESULT_READY","ABANDON").contains(Objects.toString(body.operation(),"")))throw new AuthException("INVALID_EXECUTION_RECOVERY",400);
        guard();var row=row(worker,body.executionId());Instant lease=instant(row.get("lease_until"));String status=row.get("status").toString();
        if(status.equals("DONE"))return grant(row,"DONE",lease);
        if(!Set.of("RUNNING","CLAIMED").contains(status))return grant(row,"SUPERSEDED",lease);
        if(body.operation().equals("ABANDON")){expire(row,"WORKER_RESTART");return grant(row,"ABANDONED",lease);}
        Instant now=clock.instant();
        if(!now.isBefore(lease.plusSeconds(GRACE_SECONDS))||!now.isBefore(cap(row))){expire(row,"LEASE_EXPIRED");return grant(row,"SUPERSEDED",lease);}
        if(!status.equals("RUNNING")||!"LEASED".equals(row.get("job_status"))||!trusted(worker,row)||!pending(row))return grant(row,"LEASE_LOST",lease);
        Instant until=now.plusSeconds(30);if(until.isBefore(lease))until=lease;if(until.isAfter(cap(row)))until=cap(row);
        db.update("update match_execution_attempts set lease_until=? where id=?",at(until),body.executionId());return grant(row,"UPLOAD_ALLOWED",until);
    }
    private void expire(Map<String,Object> row,String reason){
        UUID execution=(UUID)row.get("id"),job=(UUID)row.get("job_id");boolean pending=pending(row);
        int count=db.queryForObject("select count(*) from match_execution_attempts where job_id=?",Integer.class,job);
        var routes=db.queryForList("select retry_floor from worker_pool_routes where job_id=?",job);int floor=routes.isEmpty()?0:((Number)routes.getFirst().get("retry_floor")).intValue();
        boolean terminal=pending&&count-floor>=attempts;
        db.update("update match_execution_attempts set status='EXPIRED',active_marker=null,active_slot=null where id=? and active_marker=1",execution);
        db.update("update match_execution_jobs set status=? where id=? and status='LEASED'",pending&&!terminal?"QUEUED":"CANCELLED",job);
        db.update("insert into worker_execution_recoveries(execution_id,job_id,match_id,tournament_id,reason,attempt_count,terminal,recorded_at) values(?,?,?,?,?,?,?,?)",execution,job,row.get("match_id"),row.get("tournament_id"),pending?reason:"MATCH_NOT_PENDING",count,terminal,at(clock.instant()));
    }
    // Caller holds the queue guard. Reuse the durable infrastructure retry path, never a forfeit.
    void fenceWorker(UUID worker){
        var rows=db.queryForList("select a.*,j.tournament_id from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.worker_id=? and a.active_marker=1 order by a.id",worker);
        for(var row:rows)expire(row,"WORKER_RESTART");
    }
    @Scheduled(fixedDelayString="${rps.worker.lease-reap-millis:1000}")
    @Transactional
    public int expire(){
        // Cancelled/settled tournaments must not retain unclaimed queue capacity indefinitely.
        guard();db.update("update match_execution_jobs set status='CANCELLED' where status='QUEUED' and not exists(select 1 from tournament_matches m join tournaments t on t.id=m.tournament_id where m.id=match_execution_jobs.match_id and m.status='PENDING' and t.status not in ('CANCELLED','FINISHED'))");
        guard();var rows=db.queryForList("select a.*,j.tournament_id from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.active_marker=1 and (a.lease_until<=? or not exists(select 1 from tournament_matches m join tournaments t on t.id=m.tournament_id where m.id=a.match_id and m.status='PENDING' and t.status not in ('CANCELLED','FINISHED'))) order by a.lease_until,a.id limit 32",at(clock.instant().minusSeconds(GRACE_SECONDS)));
        for(var row:rows)expire(row,"LEASE_EXPIRED");return rows.size();
    }
}
