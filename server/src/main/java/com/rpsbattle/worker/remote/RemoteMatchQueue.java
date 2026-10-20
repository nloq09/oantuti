package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable pull queue. Admission is internal-only; scheduler/execution wiring is a later group. */
@Service
public class RemoteMatchQueue {
    private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;private final WorkerTelemetryService telemetry;private final int capacity;
    public RemoteMatchQueue(JdbcTemplate db,ObjectMapper json,Clock clock,WorkerTelemetryService telemetry,@Value("${rps.worker.remote-queue-capacity:10000}")int capacity){if(capacity<1||capacity>100000)throw new IllegalArgumentException("Invalid remote queue capacity");this.db=db;this.json=json;this.clock=clock;this.telemetry=telemetry;this.capacity=capacity;}
    public record BotRef(UUID artifactId,String artifactHash,String language,String runtime) {}
    public record Spec(UUID matchId,UUID tournamentId,BotRef botA,BotRef botB,String gameCoreVersion,String ruleSetVersion,long seed,int turnTimeoutMs,int matchHardTimeoutMs,int maxTurns,String minimumTrust) {}
    public record Claim(UUID requestId,Long epoch,Integer availableSlots,List<String> cachedArtifacts,String gameCoreVersion,String ruleSetVersion,Integer protocolVersion) {}
    public record Assignment(UUID jobId,UUID executionId,UUID workerId,long epoch,int slot,String status,Instant leaseUntil,Spec match) {}
    public record Batch(List<Assignment> jobs,int occupiedSlots,int effectiveFreeSlots,Instant serverNow) {}
    public record Decline(UUID executionId,String reason) {}
    public record Start(UUID executionId,Long epoch) {}
    @Transactional public Assignment gameplay(UUID worker,UUID job,Start body){
        // Reuse the same ownership, epoch, trust, heartbeat, lease and queue guards as /start.
        var assignment=start(worker,job,body);
        db.update("update match_execution_attempts set gameplay_started_at=coalesce(gameplay_started_at,?) where id=?",at(clock.instant()),body.executionId());return assignment;
    }
    private static java.sql.Timestamp at(Instant time){return java.sql.Timestamp.from(time);}
    private void guard(){db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);}
    boolean hasCapacity(){return db.queryForObject("select count(*) from match_execution_jobs where status in ('QUEUED','LEASED')",Integer.class)<capacity;}
    private String encoded(Object object){try{return json.writeValueAsString(object);}catch(Exception e){throw new IllegalArgumentException("Invalid remote protocol data");}}
    private Spec decoded(String value){try{return json.readValue(value,Spec.class);}catch(Exception e){throw new IllegalStateException("Remote job metadata invalid");}}
    private static boolean version(String v){return v!=null&&v.matches("[A-Za-z0-9._+-]{1,64}");}
    private static void bot(BotRef bot){if(bot==null||bot.artifactId()==null||bot.artifactHash()==null||!bot.artifactHash().matches("[a-f0-9]{64}")||bot.language()==null||!Set.of("PYTHON","CPP","JAVA").contains(bot.language())||!Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").get(bot.language()).equals(bot.runtime()))throw new AuthException("INVALID_REMOTE_ARTIFACT",400);}
    @Transactional
    public UUID enqueue(Spec spec) {
        if(spec==null||spec.matchId()==null||spec.tournamentId()==null||!version(spec.gameCoreVersion())||!version(spec.ruleSetVersion())||spec.turnTimeoutMs()<1||spec.turnTimeoutMs()>10000||spec.matchHardTimeoutMs()<spec.turnTimeoutMs()||spec.matchHardTimeoutMs()>3600000||spec.maxTurns()<1||spec.maxTurns()>10000||!Set.of("TRUSTED","COMMUNITY").contains(Objects.toString(spec.minimumTrust(),"")))throw new AuthException("INVALID_REMOTE_JOB",400);
        bot(spec.botA());bot(spec.botB());guard();String raw=encoded(spec),hash=WorkerIdentityService.hash(raw);
        var existing=db.queryForList("select id,spec_hash from match_execution_jobs where match_id=?",spec.matchId());
        if(!existing.isEmpty()){if(!hash.equals(existing.getFirst().get("spec_hash")))throw new AuthException("REMOTE_JOB_IMMUTABLE",409);return (UUID)existing.getFirst().get("id");}
        if(db.queryForObject("select count(*) from tournament_matches where id=? and tournament_id=? and status='PENDING'",Integer.class,spec.matchId(),spec.tournamentId())!=1)throw new AuthException("REMOTE_MATCH_NOT_PENDING",409);
        if(db.queryForObject("select count(*) from match_execution_jobs where status in ('QUEUED','LEASED')",Integer.class)>=capacity)throw new AuthException("REMOTE_QUEUE_FULL",429);
        UUID id=UUID.randomUUID();db.update("insert into match_execution_jobs values(?,?,?,'QUEUED',?,?,?,?,?,?,?,?)",id,spec.matchId(),spec.tournamentId(),raw,hash,spec.botA().language(),spec.botB().language(),spec.gameCoreVersion(),spec.ruleSetVersion(),spec.minimumTrust(),at(clock.instant()));return id;
    }
    private Map<String,Object> worker(UUID id,long epoch) {
        var rows=db.queryForList("select * from workers where id=? for update",id);if(rows.isEmpty())throw new AuthException("WORKER_NOT_FOUND",404);var worker=rows.getFirst();
        if(worker.get("status").equals("DISABLED")||worker.get("approval_status").equals("REJECTED"))throw new AuthException("WORKER_CREDENTIAL_INVALID",401);
        if(((Number)worker.get("heartbeat_epoch")).longValue()!=epoch)throw new AuthException("STALE_WORKER_SESSION",409);return worker;
    }
    private Claim normalize(Claim c) {
        if(c==null||c.requestId()==null||c.epoch()==null||c.epoch()<1||c.availableSlots()==null||c.availableSlots()<0||c.availableSlots()>256||c.cachedArtifacts()==null||c.cachedArtifacts().size()>2048||c.cachedArtifacts().stream().anyMatch(h->h==null||!h.matches("[a-f0-9]{64}"))||!version(c.gameCoreVersion())||!version(c.ruleSetVersion()))throw new AuthException("INVALID_WORKER_CLAIM",400);
        if(!Integer.valueOf(1).equals(c.protocolVersion()))throw new AuthException("WORKER_PROTOCOL_INCOMPATIBLE",409);
        return new Claim(c.requestId(),c.epoch(),c.availableSlots(),new TreeSet<>(c.cachedArtifacts()).stream().toList(),c.gameCoreVersion(),c.ruleSetVersion(),1);
    }
    @Transactional
    public Batch claim(UUID workerId,Claim body) {
        var c=normalize(body);guard();var worker=worker(workerId,c.epoch());String hash=WorkerIdentityService.hash(encoded(c));
        var receipt=db.queryForList("select request_hash from worker_claim_receipts where worker_id=? and request_id=?",workerId,c.requestId());
        if(!receipt.isEmpty()){if(!hash.equals(receipt.getFirst().get("request_hash")))throw new AuthException("CLAIM_REQUEST_CONFLICT",409);return batch(workerId,db.query("select execution_id from worker_claim_items where worker_id=? and request_id=? order by position",(r,n)->r.getObject(1,UUID.class),workerId,c.requestId()));}
        var view=telemetry.view(workerId);
        if(!view.approvalStatus().equals("APPROVED"))throw new AuthException("WORKER_APPROVAL_REQUIRED",403);
        if(!Set.of("TRUSTED","COMMUNITY").contains(view.trustLevel()))throw new AuthException("WORKER_TRUST_REQUIRED",403);
        if(!Set.of("ONLINE","BUSY").contains(view.status())||view.capabilities()==null||!view.capabilities().docker())throw new AuthException("WORKER_NOT_AVAILABLE",409);
        int limit=Math.min(16,Math.min(c.availableSlots(),view.effectiveFreeSlots()));var selected=new ArrayList<UUID>();var now=clock.instant();
        db.update("insert into worker_claim_receipts values(?,?,?,?,?)",workerId,c.requestId(),c.epoch(),hash,at(now));
        var caps=view.capabilities();var occupied=new HashSet<>(db.query("select active_slot from match_execution_attempts where worker_id=? and active_slot is not null",(r,n)->r.getInt(1),workerId));
        // FIFO: cache hints cannot make a cache-miss job wait indefinitely. Global DB admission lock
        // serializes claims across Central instances; database unique constraints are the final fence.
        var languages=caps.supportedLanguages().stream().sorted().toList();String marks=String.join(",",Collections.nCopies(languages.size(),"?"));var args=new ArrayList<Object>(List.of(c.gameCoreVersion(),c.ruleSetVersion()));args.addAll(languages);args.addAll(languages);args.add(worker.get("trust_level"));args.add(workerId);args.add(256);
        if(limit>0&&!languages.isEmpty())for(var row:preferred(db.queryForList("select j.id,j.spec_json,j.created_at,j.tournament_id from match_execution_jobs j where j.status='QUEUED' and exists(select 1 from tournament_matches m where m.id=j.match_id and m.tournament_id=j.tournament_id and m.status='PENDING') and j.game_core_version=? and j.rule_set_version=? and j.language_a in ("+marks+") and j.language_b in ("+marks+") and (j.minimum_trust='COMMUNITY' or ?='TRUSTED') and (not exists(select 1 from remote_job_routes r where r.job_id=j.id) or exists(select 1 from remote_job_routes r join worker_groups g on g.id=r.group_id join worker_group_members gm on gm.group_id=g.id join tournaments t on t.id=j.tournament_id where r.job_id=j.id and g.enabled=true and gm.worker_id=? and t.status='RUNNING')) order by j.created_at,j.id limit ?",args.toArray()),c,now)) {
            var spec=decoded(row.get("spec_json").toString());
            var policies=db.queryForList("select max_matches from tournament_worker_policies where tournament_id=? and mode='REMOTE_REQUIRED'",row.get("tournament_id"));
            if(!policies.isEmpty()&&db.queryForObject("select count(*) from match_execution_jobs where tournament_id=? and status='LEASED'",Integer.class,row.get("tournament_id"))>=((Number)policies.getFirst().get("max_matches")).intValue())continue;
            int slot=1;while(occupied.contains(slot))slot++;if(slot>caps.maxConcurrentMatches())break;
            UUID job=(UUID)row.get("id"),execution=UUID.randomUUID();Instant lease=now.plusMillis(Math.max(60000L,spec.matchHardTimeoutMs()+30000L));
            db.update("insert into match_execution_attempts(id,job_id,match_id,worker_id,heartbeat_epoch,status,active_marker,active_slot,assigned_slot,claimed_at,lease_until) values(?,?,?,?,?,'CLAIMED',1,?,?,?,?)",execution,job,spec.matchId(),workerId,c.epoch(),slot,slot,at(now),at(lease));
            db.update("update match_execution_jobs set status='LEASED' where id=?",job);db.update("insert into worker_claim_items values(?,?,?,?)",workerId,c.requestId(),execution,selected.size());selected.add(execution);occupied.add(slot);if(selected.size()>=limit)break;
        }
        return batch(workerId,selected);
    }
    private List<Map<String,Object>> preferred(List<Map<String,Object>> rows,Claim claim,Instant now){
        var cache=new HashSet<>(claim.cachedArtifacts());
        rows.sort((a,b)->{Instant x=instant(a.get("created_at")),y=instant(b.get("created_at"));boolean oldA=!x.isAfter(now.minusSeconds(30)),oldB=!y.isAfter(now.minusSeconds(30));if(oldA!=oldB)return oldA?-1:1;
            if(!oldA){int hit=Integer.compare(hits(b,cache),hits(a,cache));if(hit!=0)return hit;}int time=x.compareTo(y);return time!=0?time:a.get("id").toString().compareTo(b.get("id").toString());});return rows;
    }
    private int hits(Map<String,Object> row,Set<String> cache){var spec=decoded(row.get("spec_json").toString());return (cache.contains(spec.botA().artifactHash())?1:0)+(cache.contains(spec.botB().artifactHash())?1:0);}
    private static Instant instant(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():((java.time.OffsetDateTime)value).toInstant();}
    private Assignment assignment(UUID id) {
        return db.queryForObject("select a.*,j.spec_json from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.id=?",(r,n)->new Assignment(r.getObject("job_id",UUID.class),r.getObject("id",UUID.class),r.getObject("worker_id",UUID.class),r.getLong("heartbeat_epoch"),r.getInt("assigned_slot"),r.getString("status"),r.getTimestamp("lease_until").toInstant(),decoded(r.getString("spec_json"))),id);
    }
    private Batch batch(UUID worker,List<UUID> ids){var view=telemetry.view(worker);return new Batch(ids.stream().map(this::assignment).toList(),view.reservedSlots(),view.effectiveFreeSlots(),clock.instant());}
    public Batch assigned(UUID id){return batch(id,db.query("select id from match_execution_attempts where worker_id=? and active_marker=1 order by claimed_at,id",(r,n)->r.getObject(1,UUID.class),id));}
    @Transactional
    public Assignment start(UUID id,UUID job,Start body) {
        if(body==null||body.executionId()==null||body.epoch()==null||body.epoch()<1)throw new AuthException("INVALID_EXECUTION_START",400);
        guard();var w=worker(id,body.epoch());
        var rows=db.queryForList("select a.*,j.status as job_status,j.minimum_trust from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.id=? and a.job_id=? and a.worker_id=?",body.executionId(),job,id);
        if(rows.isEmpty())throw new AuthException("EXECUTION_NOT_ASSIGNED",404);var row=rows.getFirst();
        if(((Number)row.get("heartbeat_epoch")).longValue()!=body.epoch()||!"LEASED".equals(row.get("job_status"))||!Set.of("CLAIMED","RUNNING").contains(row.get("status"))||!clock.instant().isBefore(assignment(body.executionId()).leaseUntil()))throw new AuthException("EXECUTION_NOT_STARTABLE",409);
        var view=telemetry.view(id);
        if(!"APPROVED".equals(w.get("approval_status"))||!Set.of("ONLINE","BUSY","DRAINING").contains(view.status())||view.lastHeartbeatAt()==null||!clock.instant().isBefore(view.lastHeartbeatAt().plusSeconds(30))||!Set.of("TRUSTED","COMMUNITY").contains(w.get("trust_level"))||"TRUSTED".equals(row.get("minimum_trust"))&&!"TRUSTED".equals(w.get("trust_level")))throw new AuthException("WORKER_NOT_AVAILABLE",409);
        db.update("update match_execution_attempts set status='RUNNING',started_at=coalesce(started_at,?) where id=?",at(clock.instant()),body.executionId());return assignment(body.executionId());
    }
    @Transactional
    public Assignment decline(UUID id,UUID job,Decline body) {
        if(body==null||body.executionId()==null||body.reason()==null||!Set.of("EXECUTION_NOT_AVAILABLE","SHUTDOWN_BEFORE_START","ARTIFACT_UNAVAILABLE").contains(body.reason()))throw new AuthException("INVALID_DECLINE",400);
        guard();var rows=db.queryForList("select * from match_execution_attempts where id=? and job_id=? and worker_id=?",body.executionId(),job,id);if(rows.isEmpty())throw new AuthException("EXECUTION_NOT_ASSIGNED",404);var row=rows.getFirst();
        if(row.get("status").equals("DECLINED")){if(!body.reason().equals(row.get("decline_reason")))throw new AuthException("DECLINE_CONFLICT",409);return assignment(body.executionId());}
        if(!row.get("status").equals("CLAIMED"))throw new AuthException("EXECUTION_ALREADY_STARTED",409);
        db.update("update match_execution_attempts set status='DECLINED',active_marker=null,active_slot=null,declined_at=?,decline_reason=? where id=?",at(clock.instant()),body.reason(),body.executionId());db.update("update match_execution_jobs set status='QUEUED' where id=?",job);return assignment(body.executionId());
    }
}
