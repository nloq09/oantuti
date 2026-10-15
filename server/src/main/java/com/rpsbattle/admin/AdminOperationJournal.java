package com.rpsbattle.admin;
import com.rpsbattle.account.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
@Component
public class AdminOperationJournal {
    private final JdbcTemplate db;private final ArrayDeque<Entry> pending=new ArrayDeque<>();
    private record Entry(UUID id,UUID actor,String role,String resource,String target,String action,String result,int status,String reason,Instant at){}
    public AdminOperationJournal(JdbcTemplate db){this.db=db;}
    public void record(User actor,String resource,String target,String action,String result,int status,String reason){
        var entry=new Entry(UUID.randomUUID(),actor==null?null:actor.id().value(),actor==null?"ANONYMOUS":actor.role().name(),resource,target,action,result,status,reason,Instant.now());
        try{write(entry);}catch(RuntimeException failure){synchronized(pending){if(pending.size()==100)pending.removeFirst();pending.addLast(entry);}org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=admin_audit_write_failed");}
    }
    private void write(Entry e){db.update("insert into admin_operation_events(id,actor_user_id,actor_role,resource_type,resource_id,action,result,http_status,reason,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",e.id,e.actor,e.role,e.resource,e.target,e.action,e.result,e.status,e.reason,java.sql.Timestamp.from(e.at));}
    public int pending(){synchronized(pending){return pending.size();}}
    @Scheduled(fixedDelay=10000) public void retry(){synchronized(pending){for(int i=0;i<10&&!pending.isEmpty();i++){var e=pending.getFirst();try{if(Objects.requireNonNull(db.queryForObject("select count(*) from admin_operation_events where id=?",Long.class,e.id))==0)write(e);pending.removeFirst();}catch(RuntimeException unavailable){break;}}}}
}
