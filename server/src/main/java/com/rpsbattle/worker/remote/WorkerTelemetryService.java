package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerTelemetryService {
    private final JdbcTemplate db;
    private final Clock clock;
    public WorkerTelemetryService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    public record Capabilities(Integer cpuCores,Long memoryMb,String os,Boolean docker,Set<String> supportedLanguages,Integer maxConcurrentMatches,Integer maxBotProcesses,String workerVersion,Integer protocolVersion) {}
    public record Heartbeat(Long epoch,Long sequence,Integer activeMatches,Integer freeSlots,Integer activeBotProcesses,Double cpuUsage,Double memoryUsage,Long diskFreeMb,Long cacheSizeMb) {}
    public record Session(long epoch,int heartbeatIntervalSeconds,int offlineAfterSeconds) {}
    public record View(UUID id,String name,String status,String approvalStatus,String trustLevel,long revision,String remoteAddress,String workerVersion,int protocolVersion,Instant lastHeartbeatAt,Capabilities capabilities,Heartbeat heartbeat,int effectiveFreeSlots,int reservedSlots,boolean credentialRevoked,Integer adminMaxMatches,int capacityBudget) {}
    public record Page(List<View> items,long total,int page,int size) {}
    public record Review(long revision,String decision,String trustLevel,String reason) {}
    private static java.sql.Timestamp at(Instant instant){return java.sql.Timestamp.from(instant);}
    private Map<String,Object> lock(UUID id) {
        var rows=db.queryForList("select * from workers where id=? for update",id);
        if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);
        var row=rows.getFirst();
        if(row.get("status").equals("DISABLED") || row.get("approval_status").equals("REJECTED"))throw new AuthException("WORKER_CREDENTIAL_INVALID",401);
        return row;
    }
    private static boolean integer(Integer n,int min,int max){return n!=null&&n>=min&&n<=max;}
    private static boolean positive(Long n,long max){return n!=null&&n>0&&n<=max;}
    private static boolean percent(Double n){return n==null||Double.isFinite(n)&&n>=0&&n<=100;}
    private static void validate(Capabilities c) {
        if(c==null||!integer(c.cpuCores(),1,1024)||!positive(c.memoryMb(),1073741824)||c.os()==null||!c.os().matches("[\\p{L}\\p{N} ._()+-]{1,80}")||c.docker()==null||c.supportedLanguages()==null||c.supportedLanguages().size()>3||!Set.of("PYTHON","CPP","JAVA").containsAll(c.supportedLanguages())||!integer(c.maxConcurrentMatches(),1,256)||!integer(c.maxBotProcesses(),2,512)||c.maxBotProcesses()<2*c.maxConcurrentMatches()||c.workerVersion()==null||!c.workerVersion().matches("[A-Za-z0-9._+-]{1,64}"))throw new AuthException("INVALID_WORKER_CAPABILITIES",400);
        if(!Integer.valueOf(1).equals(c.protocolVersion()))throw new AuthException("WORKER_PROTOCOL_INCOMPATIBLE",409);
        if(!c.docker()&&!c.supportedLanguages().isEmpty())throw new AuthException("INVALID_WORKER_CAPABILITIES",400);
    }
    @Transactional
    public Session capabilities(UUID id,Capabilities body) {
        validate(body);lock(id);var reserved=db.queryForMap("select count(*) as occupied,coalesce(max(active_slot),0) as highest from match_execution_attempts where worker_id=? and active_marker=1",id);if(((Number)reserved.get("highest")).intValue()>body.maxConcurrentMatches()||((Number)reserved.get("occupied")).intValue()*2>body.maxBotProcesses())throw new AuthException("WORKER_CAPACITY_RESERVED",409);var now=clock.instant();
        // A new startup invalidates delayed heartbeat packets from any prior agent instance.
        db.update("delete from worker_heartbeats where worker_id=?",id);
        db.update("delete from worker_capabilities where worker_id=?",id);
        db.update("insert into worker_capabilities values(?,?,?,?,?,?,?,?,?)",id,body.cpuCores(),body.memoryMb(),body.os(),body.docker(),String.join(",",new TreeSet<>(body.supportedLanguages())),body.maxConcurrentMatches(),body.maxBotProcesses(),at(now));
        db.update("update workers set heartbeat_epoch=heartbeat_epoch+1,worker_version=?,last_heartbeat_at=null,status=case when status='DRAINING' then 'DRAINING' else 'STARTING' end where id=?",body.workerVersion(),id);
        return new Session(db.queryForObject("select heartbeat_epoch from workers where id=?",Long.class,id),5,30);
    }
    @Transactional
    public View heartbeat(UUID id,Heartbeat h,String remoteAddress) {
        var worker=lock(id);long epoch=((Number)worker.get("heartbeat_epoch")).longValue();
        if(h==null||!positive(h.epoch(),Long.MAX_VALUE)||!positive(h.sequence(),Long.MAX_VALUE)||!integer(h.activeMatches(),0,256)||!integer(h.freeSlots(),0,256)||!integer(h.activeBotProcesses(),0,512)||!percent(h.cpuUsage())||!percent(h.memoryUsage())||h.diskFreeMb()==null||h.diskFreeMb()<0||h.cacheSizeMb()==null||h.cacheSizeMb()<0)throw new AuthException("INVALID_WORKER_HEARTBEAT",400);
        if(h.epoch()!=epoch)throw new AuthException("STALE_WORKER_SESSION",409);
        var c=capabilitiesOf(id);if(c==null)throw new AuthException("WORKER_CAPABILITIES_REQUIRED",409);
        if(h.activeMatches()>c.maxConcurrentMatches()||h.freeSlots()>c.maxConcurrentMatches()-h.activeMatches()||h.activeBotProcesses()>c.maxBotProcesses())throw new AuthException("INVALID_WORKER_HEARTBEAT",400);
        var previous=db.queryForList("select sequence_number from worker_heartbeats where worker_id=?",id);
        // Retries/out-of-order deliveries do not refresh liveness or replace newer metrics.
        if(!previous.isEmpty()&&((Number)previous.getFirst().get("sequence_number")).longValue()>=h.sequence())return view(id);
        var now=clock.instant();String status=!c.docker()||c.supportedLanguages().isEmpty()?"DEGRADED":h.freeSlots()==0?"BUSY":"ONLINE";
        if(Set.of("DRAINING","DISABLED").contains(worker.get("status")))status=worker.get("status").toString();
        db.update("delete from worker_heartbeats where worker_id=?",id);
        db.update("insert into worker_heartbeats values(?,?,?,?,?,?,?,?,?,?,?)",id,epoch,h.sequence(),h.activeMatches(),h.freeSlots(),h.activeBotProcesses(),h.cpuUsage(),h.memoryUsage(),h.diskFreeMb(),h.cacheSizeMb(),at(now));
        db.update("update workers set status=?,last_heartbeat_at=?,remote_address=? where id=?",status,at(now),remoteAddress==null?null:remoteAddress.substring(0,Math.min(64,remoteAddress.length())),id);
        return view(id);
    }
    private Capabilities capabilitiesOf(UUID id) {
        var list=db.query("select * from worker_capabilities where worker_id=?",(r,n)->new Capabilities(r.getInt("cpu_cores"),r.getLong("memory_mb"),r.getString("os"),r.getBoolean("docker_enabled"),r.getString("supported_languages").isEmpty()?Set.of():Set.of(r.getString("supported_languages").split(",")),r.getInt("max_concurrent_matches"),r.getInt("max_bot_processes"),null,1),id);
        return list.isEmpty()?null:list.getFirst();
    }
    public View view(UUID id) {
        var rows=db.query("select * from workers where id=?",(r,n)->{
            Instant last=r.getTimestamp("last_heartbeat_at")==null?null:r.getTimestamp("last_heartbeat_at").toInstant();
            Instant first=r.getTimestamp("registered_at").toInstant();String status=r.getString("status");
            if(!Set.of("DISABLED","DRAINING").contains(status)&&!clock.instant().isBefore((last==null?first:last).plusSeconds(30)))status="OFFLINE";
            return new View(id,r.getString("name"),status,r.getString("approval_status"),r.getString("trust_level"),r.getLong("revision"),r.getString("remote_address"),r.getString("worker_version"),r.getInt("protocol_version"),last,null,null,0,0,false,null,0);
        },id);
        if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);
        var w=rows.getFirst();var c=capabilitiesOf(id);
        var samples=db.query("select * from worker_heartbeats where worker_id=?",(r,n)->new Heartbeat(r.getLong("heartbeat_epoch"),r.getLong("sequence_number"),r.getInt("active_matches"),r.getInt("offered_slots"),r.getInt("active_bot_processes"),(Double)r.getObject("cpu_usage"),(Double)r.getObject("memory_usage"),r.getLong("disk_free_mb"),r.getLong("cache_size_mb")),id);
        var h=samples.isEmpty()?null:samples.getFirst();int free=0;int reserved=db.queryForObject("select count(*) from match_execution_attempts where worker_id=? and active_marker=1",Integer.class,id);
        if(w.approvalStatus().equals("APPROVED")&&Set.of("ONLINE","BUSY").contains(w.status())&&c!=null&&h!=null)free=Math.max(0,Math.min(h.freeSlots(),Math.min(c.maxConcurrentMatches()-Math.max(h.activeMatches(),reserved),(c.maxBotProcesses()-Math.max(h.activeBotProcesses(),reserved*2))/2)));
        int budget=0;Integer adminLimit=null;
        if(c!=null&&h!=null){
            var overrides=db.queryForList("select max_matches from worker_capacity_limits where worker_id=?",id);
            budget=Math.min(c.maxConcurrentMatches(),Math.min(c.cpuCores(),(int)Math.max(0,(c.memoryMb()-512)/896)));
            if(!overrides.isEmpty()){adminLimit=((Number)overrides.getFirst().get("max_matches")).intValue();budget=Math.min(budget,adminLimit);}
            if(h.cpuUsage()!=null&&h.cpuUsage()>=98||h.memoryUsage()!=null&&h.memoryUsage()>=90)budget=0;
            else if(h.cpuUsage()!=null&&h.cpuUsage()>=90)budget=Math.min(budget,Math.max(1,budget/4));
            free=Math.min(free,Math.max(0,budget-Math.max(reserved,h.activeMatches())));
        }
        boolean revoked=db.queryForObject("select count(*) from worker_credentials where worker_id=? and revoked_at is not null",Integer.class,id)>0;
        String status=w.status().equals("DRAINING")&&reserved==0?"DRAINED":w.approvalStatus().equals("APPROVED")&&Set.of("ONLINE","BUSY").contains(w.status())&&free==0?"BUSY":w.status();
        return new View(w.id(),w.name(),status,w.approvalStatus(),w.trustLevel(),w.revision(),w.remoteAddress(),w.workerVersion(),w.protocolVersion(),w.lastHeartbeatAt(),c,h,free,reserved,revoked,adminLimit,budget);
    }
    public Page list(int page,int size) {
        if(page<0||page>100000||size<1||size>100)throw new AuthException("INVALID_PAGE",400);
        var ids=db.query("select id from workers order by registered_at desc,id limit ? offset ?",(r,n)->r.getObject(1,UUID.class),size,(long)page*size);
        return new Page(ids.stream().map(this::view).toList(),db.queryForObject("select count(*) from workers",Long.class),page,size);
    }
    @Transactional
    public View review(UUID id,UUID actor,Review review) {
        var before=lock(id);
        if(review==null||review.reason()==null||review.reason().isBlank()||review.reason().length()>500||!Set.of("APPROVE","REJECT").contains(review.decision()==null?"":review.decision())||review.decision().equals("APPROVE")&&!Set.of("TRUSTED","COMMUNITY").contains(review.trustLevel()==null?"":review.trustLevel()))throw new AuthException("INVALID_WORKER_REVIEW",400);
        if(((Number)before.get("revision")).longValue()!=review.revision()||!before.get("approval_status").equals("PENDING_APPROVAL"))throw new AuthException("STALE_WORKER_REVIEW",409);
        String approval=review.decision().equals("APPROVE")?"APPROVED":"REJECTED",trust=approval.equals("APPROVED")?review.trustLevel():"UNTRUSTED";
        db.update("update workers set approval_status=?,trust_level=?,revision=revision+1 where id=?",approval,trust,id);
        db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at,reason,before_value,after_value) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),"WORKER_"+approval,id,actor,at(clock.instant()),review.reason().strip(),before.get("approval_status")+":"+before.get("trust_level"),approval+":"+trust);
        return view(id);
    }
    @Scheduled(fixedDelay=5000)
    public void expireOffline() {
        db.update("update workers set status='OFFLINE' where status in ('STARTING','ONLINE','BUSY','DEGRADED') and coalesce(last_heartbeat_at,registered_at)<=?",at(clock.instant().minusSeconds(30)));
    }
}
