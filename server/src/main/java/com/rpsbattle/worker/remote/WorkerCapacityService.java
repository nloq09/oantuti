package com.rpsbattle.worker.remote;
import com.rpsbattle.auth.AuthException;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class WorkerCapacityService {
    public record Limit(long revision,Integer maxMatches,String reason){}
    private final JdbcTemplate db;private final Clock clock;
    public WorkerCapacityService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    private void guard(){db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);}
    private static void reason(String reason){if(reason==null||reason.isBlank()||reason.length()>500)throw new AuthException("INVALID_WORKER_POLICY",400);}
    private void event(String type,UUID resource,UUID actor,String reason,String before,String after){db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at,reason,before_value,after_value) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),type,resource,actor,java.sql.Timestamp.from(clock.instant()),reason.strip(),before,after);}
    @Transactional public void limit(UUID id,UUID actor,Limit body){
        if(body==null||body.maxMatches()!=null&&(body.maxMatches()<1||body.maxMatches()>256))throw new AuthException("INVALID_WORKER_POLICY",400);reason(body.reason());guard();
        var rows=db.queryForList("select revision from workers where id=? for update",id);if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);if(((Number)rows.getFirst().get("revision")).longValue()!=body.revision())throw new AuthException("STALE_WORKER_CONTROL",409);
        db.update("delete from worker_capacity_limits where worker_id=?",id);if(body.maxMatches()!=null)db.update("insert into worker_capacity_limits values(?,?)",id,body.maxMatches());db.update("update workers set revision=revision+1 where id=?",id);event("WORKER_CAPACITY_CHANGED",id,actor,body.reason(),null,Objects.toString(body.maxMatches(),"AUTO"));
    }
}
