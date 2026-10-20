package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control and allocation use the same database fence, including across Central instances. */
@Service
public class WorkerControlService {
    public record Command(long revision,String action,String reason) {}
    private final JdbcTemplate db;private final Clock clock;private final WorkerTelemetryService telemetry;private final RemoteLeaseService leases;
    public WorkerControlService(JdbcTemplate db,Clock clock,WorkerTelemetryService telemetry,RemoteLeaseService leases){this.db=db;this.clock=clock;this.telemetry=telemetry;this.leases=leases;}
    @Transactional
    public WorkerTelemetryService.View control(UUID id,UUID actor,Command command){
        if(command==null||command.revision()<0||!Set.of("DRAIN","RESUME","DISABLE","REVOKE").contains(Objects.toString(command.action(),""))||command.reason()==null||command.reason().isBlank()||command.reason().length()>500)throw new AuthException("INVALID_WORKER_CONTROL",400);
        db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);
        var rows=db.queryForList("select * from workers where id=? for update",id);
        if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);var before=rows.getFirst();
        if(((Number)before.get("revision")).longValue()!=command.revision())throw new AuthException("STALE_WORKER_CONTROL",409);
        boolean revoked=db.queryForObject("select count(*) from worker_credentials where worker_id=? and revoked_at is not null",Integer.class,id)>0;
        String action=command.action(),status=before.get("status").toString();
        if(revoked||!action.equals("REVOKE")&&!before.get("approval_status").equals("APPROVED")||action.equals("DRAIN")&&status.equals("DISABLED")||action.equals("RESUME")&&!Set.of("DRAINING","DISABLED").contains(status))throw new AuthException("WORKER_CONTROL_CONFLICT",409);
        String after=switch(action){case "DRAIN"->"DRAINING";case "RESUME"->"STARTING";default->"DISABLED";};
        // Disable cancels current work; drain is the explicit finish-current policy.
        if(action.equals("DISABLE")||action.equals("REVOKE"))leases.fenceWorker(id);
        db.update("update workers set status=?,revision=revision+1 where id=?",after,id);
        if(action.equals("DISABLE")||action.equals("REVOKE")||action.equals("RESUME"))db.update("update workers set heartbeat_epoch=heartbeat_epoch+1,last_heartbeat_at=null where id=?",id);
        if(action.equals("REVOKE"))db.update("update worker_credentials set revoked_at=? where worker_id=? and revoked_at is null",java.sql.Timestamp.from(clock.instant()),id);
        db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at,reason,before_value,after_value) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),"WORKER_"+action,id,actor,java.sql.Timestamp.from(clock.instant()),command.reason().strip(),status,after);
        return telemetry.view(id);
    }
    public List<Map<String,Object>> events(UUID id){
        telemetry.view(id);
        return db.queryForList("select event_type,actor_user_id,occurred_at,reason,before_value,after_value from worker_identity_events where resource_id=? order by occurred_at desc,id desc limit 100",id);
    }
}
