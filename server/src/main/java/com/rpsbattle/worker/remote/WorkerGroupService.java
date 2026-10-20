package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerGroupService {
    public record Group(UUID id,String name,boolean enabled,long revision,List<UUID> workers){}
    public record Change(long revision,Boolean enabled,UUID workerId,Boolean member,String reason){}
    public record Limit(long revision,Integer maxMatches,String reason){}
    private final JdbcTemplate db;private final Clock clock;
    public WorkerGroupService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    private void guard(){db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);}
    private static void reason(String reason){if(reason==null||reason.isBlank()||reason.length()>500)throw new AuthException("INVALID_WORKER_POLICY",400);}
    private void event(String type,UUID resource,UUID actor,String reason,String before,String after){db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at,reason,before_value,after_value) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),type,resource,actor,java.sql.Timestamp.from(clock.instant()),reason.strip(),before,after);}
    public Group group(UUID id){var rows=db.query("select * from worker_groups where id=?",(r,n)->new Group(id,r.getString("name"),r.getBoolean("enabled"),r.getLong("revision"),db.query("select worker_id from worker_group_members where group_id=? order by worker_id",(m,k)->m.getObject(1,UUID.class),id)),id);if(rows.isEmpty())throw new AuthException("WORKER_GROUP_NOT_FOUND",404);return rows.getFirst();}
    public List<Group> list(){return db.query("select id from worker_groups order by name limit 100",(r,n)->r.getObject(1,UUID.class)).stream().map(this::group).toList();}
    @Transactional public Group create(String name,UUID actor,String reason){
        reason(reason);if(name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,63}"))throw new AuthException("INVALID_WORKER_GROUP",400);guard();
        if(db.queryForObject("select count(*) from worker_groups",Integer.class)>=100)throw new AuthException("WORKER_GROUP_LIMIT",409);
        if(db.queryForObject("select count(*) from worker_groups where name=?",Integer.class,name)>0)throw new AuthException("WORKER_GROUP_EXISTS",409);
        UUID id=UUID.randomUUID();db.update("insert into worker_groups(id,name) values(?,?)",id,name);event("WORKER_GROUP_CREATED",id,actor,reason,null,name);return group(id);
    }
    @Transactional public Group change(UUID id,UUID actor,Change body){
        if(body==null||body.revision()<0||(body.workerId()==null)!=(body.member()==null)||body.enabled()==null&&body.workerId()==null)throw new AuthException("INVALID_WORKER_GROUP",400);reason(body.reason());guard();var before=group(id);
        if(before.revision()!=body.revision())throw new AuthException("STALE_WORKER_GROUP",409);
        if(body.workerId()!=null){if(db.queryForObject("select count(*) from workers where id=?",Integer.class,body.workerId())!=1)throw new AuthException("WORKER_NOT_FOUND",404);db.update("delete from worker_group_members where group_id=? and worker_id=?",id,body.workerId());if(body.member())db.update("insert into worker_group_members values(?,?)",id,body.workerId());}
        db.update("update worker_groups set revision=revision+1,enabled=? where id=?",body.enabled()==null?before.enabled():body.enabled(),id);
        event("WORKER_GROUP_CHANGED",id,actor,body.reason(),Long.toString(before.revision()),body.workerId()==null?"enabled="+body.enabled():body.workerId()+":"+body.member());return group(id);
    }
    @Transactional public void limit(UUID id,UUID actor,Limit body){
        if(body==null||body.maxMatches()!=null&&(body.maxMatches()<1||body.maxMatches()>256))throw new AuthException("INVALID_WORKER_POLICY",400);reason(body.reason());guard();
        var rows=db.queryForList("select revision from workers where id=? for update",id);if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);if(((Number)rows.getFirst().get("revision")).longValue()!=body.revision())throw new AuthException("STALE_WORKER_CONTROL",409);
        db.update("delete from worker_capacity_limits where worker_id=?",id);if(body.maxMatches()!=null)db.update("insert into worker_capacity_limits values(?,?)",id,body.maxMatches());db.update("update workers set revision=revision+1 where id=?",id);event("WORKER_CAPACITY_CHANGED",id,actor,body.reason(),null,Objects.toString(body.maxMatches(),"AUTO"));
    }
}
