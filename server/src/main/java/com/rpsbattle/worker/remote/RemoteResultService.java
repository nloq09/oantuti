package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.*;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.replay.*;
import com.rpsbattle.online.*;
import com.rpsbattle.api.mapper.GameStateMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable acceptance is separate from tournament projection, so a retry never loses a result. */
@Service
public class RemoteResultService {
    public static final int REPLAY_LIMIT=15*1024*1024, REQUEST_LIMIT=22*1024*1024;
    public record MoveTiming(Long nanosA,Long nanosB,Integer countA,Integer countB) {}
    public record Result(UUID executionId,UUID matchId,String winner,String loser,String reason,Integer turnCount,Long durationMs,String artifactHashA,String artifactHashB,Long seed,String replayChecksum,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) MoveTiming moveTiming) {
        public Result(UUID executionId,UUID matchId,String winner,String loser,String reason,Integer turnCount,Long durationMs,String artifactHashA,String artifactHashB,Long seed,String replayChecksum){this(executionId,matchId,winner,loser,reason,turnCount,durationMs,artifactHashA,artifactHashB,seed,replayChecksum,null);}
    }
    public record Upload(Result result,String replayBase64) {}
    public record Receipt(UUID executionId,UUID jobId,UUID matchId,String resultHash,String status) {}
    private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;private final GameStateMapper mapper;
    public RemoteResultService(JdbcTemplate db,ObjectMapper json,Clock clock,GameStateMapper mapper){this.db=db;this.json=json;this.clock=clock;this.mapper=mapper;}
    String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new AuthException("INVALID_REMOTE_RESULT",400);}}
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    @Transactional
    public Receipt accept(UUID worker,UUID job,Upload upload) {
        if(upload==null||upload.result()==null||upload.replayBase64()==null||upload.replayBase64().length()>(REPLAY_LIMIT+2)/3*4)throw new AuthException("INVALID_REMOTE_RESULT",400);
        var r=upload.result();
        if(r.executionId()==null||r.matchId()==null||r.turnCount()==null||r.turnCount()<1||r.turnCount()>10000||r.durationMs()==null||r.durationMs()<0||r.durationMs()>3600000||r.seed()==null||!Set.of("ENGINE_RESULT","BOT_FAILED").contains(Objects.toString(r.reason(),""))||!digest(r.artifactHashA())||!digest(r.artifactHashB())||!digest(r.replayChecksum())||!(r.winner()==null&&r.loser()==null||"P1".equals(r.winner())&&"P2".equals(r.loser())||"P2".equals(r.winner())&&"P1".equals(r.loser())))throw new AuthException("INVALID_REMOTE_RESULT",400);
        validateTiming(r);
        // Fence duplicate completion and claims in the same order as the queue service.
        db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);
        var rows=db.queryForList("select a.*,j.spec_json,j.spec_hash,j.status as job_status,w.approval_status,w.trust_level,w.status as worker_status from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id join workers w on w.id=a.worker_id where a.id=? and a.job_id=? and a.worker_id=?",r.executionId(),job,worker);
        if(rows.isEmpty())throw new AuthException("EXECUTION_NOT_ASSIGNED",404);var row=rows.getFirst();
        byte[] bytes;try{bytes=Base64.getDecoder().decode(upload.replayBase64());}catch(Exception e){throw new AuthException("INVALID_REMOTE_REPLAY",400);}
        if(bytes.length==0||bytes.length>REPLAY_LIMIT||!hash(bytes).equals(r.replayChecksum()))throw new AuthException("REPLAY_CHECKSUM_MISMATCH",422);
        String encoded=encode(r),resultHash=WorkerIdentityService.hash(encoded);
        var previous=db.queryForList("select result_hash from worker_result_receipts where execution_id=?",r.executionId());
        if(!previous.isEmpty()){
            if(!resultHash.equals(previous.getFirst().get("result_hash")))throw new AuthException("RESULT_CONFLICT",409);
            return new Receipt(r.executionId(),job,r.matchId(),resultHash,"ACCEPTED");
        }
        String raw=row.get("spec_json").toString();if(!WorkerIdentityService.hash(raw).equals(row.get("spec_hash")))throw new AuthException("REMOTE_JOB_CORRUPT",409);
        RemoteMatchQueue.Spec spec;try{spec=json.readValue(raw,RemoteMatchQueue.Spec.class);}catch(Exception e){throw new AuthException("REMOTE_JOB_CORRUPT",409);}
        WorkerScope.require(db,worker,spec.tournamentId());
        if(!r.matchId().equals(spec.matchId())||!r.artifactHashA().equals(spec.botA().artifactHash())||!r.artifactHashB().equals(spec.botB().artifactHash())||r.seed()!=spec.seed()||r.turnCount()>spec.maxTurns()||r.durationMs()>spec.matchHardTimeoutMs())throw new AuthException("RESULT_JOB_MISMATCH",422);
        Object expiry=row.get("lease_until");Instant lease=expiry instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)expiry).toInstant();
        if(!"RUNNING".equals(row.get("status"))||!"LEASED".equals(row.get("job_status"))||!clock.instant().isBefore(lease))throw new AuthException("EXECUTION_NOT_COMPLETABLE",409);
        if(!"APPROVED".equals(row.get("approval_status"))||"DISABLED".equals(row.get("worker_status"))||!Set.of("TRUSTED","COMMUNITY").contains(row.get("trust_level"))||spec.minimumTrust().equals("TRUSTED")&&!"TRUSTED".equals(row.get("trust_level")))throw new AuthException("WORKER_NOT_AVAILABLE",409);
        if(db.queryForObject("select count(*) from tournaments t join tournament_matches m on m.tournament_id=t.id where t.id=? and m.id=? and t.status in ('RUNNING','PAUSED','RECOVERY_REQUIRED') and m.status='PENDING'",Integer.class,spec.tournamentId(),spec.matchId())!=1)throw new AuthException("REMOTE_MATCH_NOT_PENDING",409);
        validate(spec,r,bytes);
        db.update("insert into worker_result_receipts(execution_id,job_id,match_id,worker_id,tournament_id,result_hash,result_json,replay_json,received_at) values(?,?,?,?,?,?,?,?,?)",r.executionId(),job,r.matchId(),worker,spec.tournamentId(),resultHash,encoded,new String(bytes,java.nio.charset.StandardCharsets.UTF_8),java.sql.Timestamp.from(clock.instant()));
        db.update("update match_execution_attempts set status='DONE',active_marker=null,active_slot=null where id=?",r.executionId());
        db.update("update match_execution_jobs set status='DONE' where id=?",job);
        return new Receipt(r.executionId(),job,r.matchId(),resultHash,"ACCEPTED");
    }
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static void validateTiming(Result r){
        var m=r.moveTiming();if(m==null)return; // Old workers do not have measured timing; never fabricate zero-duration moves.
        long budget=(r.durationMs()+1)*1_000_000L;
        if(m.nanosA()==null||m.nanosB()==null||m.countA()==null||m.countB()==null
            ||m.nanosA()<0||m.nanosB()<0||m.nanosA()>budget||m.nanosB()>budget||m.nanosA()+m.nanosB()>budget
            ||m.countA()<0||m.countB()<0||m.countA()>r.turnCount()||m.countB()>r.turnCount()||m.countA()+m.countB()==0
            ||m.countA()==0&&m.nanosA()!=0||m.countB()==0&&m.nanosB()!=0)throw new AuthException("INVALID_REMOTE_TIMING",422);
    }
    private void validate(RemoteMatchQueue.Spec spec,Result r,byte[] bytes) {
        try {
            var payload=json.readTree(bytes);var initialNode=payload.path("initialState");
            if(initialNode.path("rows").asInt()!=9||initialNode.path("cols").asInt()!=9||initialNode.path("pieces").size()!=36||initialNode.path("players").size()!=2||!payload.path("events").isArray()||payload.path("events").size()>16*(spec.maxTurns()+1)+4)throw new IllegalArgumentException();
            var replay=new ReplayService();var log=replay.read(bytes);var state=replay.replay(log);
            var winner=state.result().orElseThrow().winner().map(p->p.value()).orElse(null);
            if(!log.matchId().equals(spec.matchId().toString())||!log.ruleSet().equals(spec.ruleSetVersion())||!Objects.equals(log.seed(),spec.seed())||state.turnNumber()!=r.turnCount()||!Objects.equals(winner,r.winner())||!log.initialState().players().stream().map(p->p.id().value()).toList().equals(List.of("P1","P2")))throw new IllegalArgumentException();
            var initial=new OnlineMatch(spec.matchId().toString(),MatchMode.HUMAN_VS_HUMAN,spec.seed(),clock,Duration.ofMillis(spec.turnTimeoutMs()));
            initial.attachBot(OnlineMatch.P1,(v,a)->null,"Bot A");initial.join();initial.attachBot(OnlineMatch.P2,(v,a)->null,"Bot B");initial.prepareBots();
            if(!mapper.toDto(initial.state().withPhase(com.rpsbattle.game.state.GamePhase.READY),List.of(),List.of()).equals(mapper.toDto(log.initialState(),List.of(),List.of())))throw new IllegalArgumentException();
        }catch(Exception e){throw new AuthException("INVALID_REMOTE_REPLAY",422);}
    }
    /** Called with the tournament paused and its monitor held. Fence claims that began before pause committed. */
    @Transactional public Set<String> inFlightMatches(String tournament){
        db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);
        return new HashSet<>(db.query("select j.match_id from match_execution_jobs j join tournament_matches m on m.id=j.match_id where j.tournament_id=? and j.status in ('LEASED','DONE') and m.status='PENDING'",(r,n)->r.getObject(1,UUID.class).toString(),UUID.fromString(tournament)));
    }
    public boolean owns(String match){return db.queryForObject("select count(*) from match_execution_jobs where match_id=? and status in ('QUEUED','LEASED','DONE')",Integer.class,UUID.fromString(match))>0;}
    public Set<String> pendingOwnership(){return new HashSet<>(db.query("select j.match_id from match_execution_jobs j join tournament_matches m on m.id=j.match_id where j.status in ('QUEUED','LEASED','DONE') and m.status='PENDING'",(r,n)->r.getObject(1,UUID.class).toString()));}
}
