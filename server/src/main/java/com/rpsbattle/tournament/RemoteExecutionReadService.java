package com.rpsbattle.tournament;

import com.rpsbattle.auth.AuthException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Tournament-scoped scalar facts. Does not deserialize specs, results, replays or worker secrets. */
@Service
public class RemoteExecutionReadService {
 private final JdbcTemplate db;private final Clock clock;private final TournamentService tournaments;private final TournamentAuthorizationService authorization;
 public RemoteExecutionReadService(JdbcTemplate db,Clock clock,TournamentService tournaments,TournamentAuthorizationService authorization){this.db=db;this.clock=clock;this.tournaments=tournaments;this.authorization=authorization;}
 public static final List<String> STATES=List.of("QUEUED","ASSIGNED","STARTING","RUNNING_REMOTE","RESULT_PENDING","FINISHED","RETRYING","RECOVERING","ERROR","CANCELLED","UNKNOWN");
 public record Item(UUID matchId,UUID jobId,UUID executionId,String matchStatus,String executionState,String workerDisplayName,String workerStatus,int attempt,Integer queuePosition,Instant queuedAt,Instant claimedAt,Instant startingAt,Instant startedAt,Instant lastHeartbeatAt,Instant leaseUntil,String leaseState,Instant resultReceivedAt,String projectionStatus,String failureCategory){}
 public record Page(String tournamentId,long revision,Instant serverNow,int page,int size,long total,Map<String,Long> counts,List<Item> items,String queuePositionNote){}
 static Instant instant(Object value){return value==null?null:value instanceof java.sql.Timestamp t?t.toInstant():value instanceof OffsetDateTime t?t.toInstant():(Instant)value;}
 static String string(Map<String,Object> row,String key){return Objects.toString(row.get(key),null);}
 public static String state(String tournament,String match,String job,String attempt,boolean receipt,String projection,boolean terminalRecovery,boolean gameplay,boolean expired,int attemptCount){
  if("FINISHED".equals(match))return "FINISHED";
  if("CANCELLED".equals(tournament))return "CANCELLED";
  if("FAILED".equals(match)||terminalRecovery)return "ERROR";
  if(receipt)return "PENDING".equals(projection)?"RESULT_PENDING":"APPLIED".equals(projection)?"RESULT_PENDING":"ERROR";
  if("QUEUED".equals(job))return attemptCount>0?"RETRYING":"QUEUED";
  if("LEASED".equals(job)){
   if(expired)return "RECOVERING";
   if("CLAIMED".equals(attempt))return "ASSIGNED";
   if("RUNNING".equals(attempt))return gameplay?"RUNNING_REMOTE":"STARTING";
   return "UNKNOWN";
  }
  return "CANCELLED".equals(job)?"CANCELLED":"UNKNOWN";
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page read(Authentication auth,String id,int page,int size,String filter){
  var t=tournaments.find(id);authorization.requireManager(auth,t);return snapshot(id,page,size,filter,null);
 }
 // Internal projection; the caller authorizes access and selects only permitted match IDs.
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Item> forMatches(String id,Set<String> matches){
  if(matches.isEmpty())return Map.of();return snapshot(id,0,100,null,matches).items().stream().collect(java.util.stream.Collectors.toMap(i->i.matchId().toString(),i->i));
 }
 private Page snapshot(String id,int page,int size,String filter,Set<String> matches){
  var t=tournaments.find(id);if(page<0||page>10000||size<1||size>100)throw new AuthException("INVALID_PAGING",400);if(filter!=null&&!STATES.contains(filter))throw new AuthException("INVALID_FILTER",400);
  long revision; synchronized(t){revision=t.revision;}
  var now=clock.instant();
  var args=new ArrayList<Object>();args.add(UUID.fromString(id));if(matches!=null)matches.forEach(m->args.add(UUID.fromString(m)));
  String scope=matches==null?"":" and j.match_id in ("+String.join(",",Collections.nCopies(matches.size(),"?"))+")";
  var rows=db.queryForList("select j.id as job_id,j.match_id,j.status as job_status,j.created_at,m.status as match_status,t.status as tournament_status,a.id as execution_id,a.status as attempt_status,a.claimed_at,a.started_at,a.gameplay_started_at,a.lease_until,w.name as worker_name,w.status as worker_status,w.last_heartbeat_at,r.received_at,r.projection_status,r.projection_error,x.reason as recovery_reason,x.terminal as terminal_recovery,(select count(*) from match_execution_attempts n where n.job_id=j.id) as attempt_count from match_execution_jobs j join tournament_matches m on m.id=j.match_id and m.tournament_id=j.tournament_id join tournaments t on t.id=j.tournament_id left join match_execution_attempts a on a.job_id=j.id and (a.active_marker=1 or (not exists(select 1 from match_execution_attempts active where active.job_id=j.id and active.active_marker=1) and not exists(select 1 from match_execution_attempts newer where newer.job_id=j.id and (newer.claimed_at>a.claimed_at or (newer.claimed_at=a.claimed_at and newer.id>a.id))))) left join workers w on w.id=a.worker_id left join worker_result_receipts r on r.execution_id=a.id and r.tournament_id=j.tournament_id left join worker_execution_recoveries x on x.execution_id=a.id and x.tournament_id=j.tournament_id where j.tournament_id=?"+scope+" order by j.created_at,j.id",args.toArray());
  var items=new ArrayList<Item>();var counts=new LinkedHashMap<String,Long>();STATES.forEach(s->counts.put(s,0L));int position=0;
  for(var row:rows){String job=string(row,"job_status"),match=string(row,"match_status"),projection=string(row,"projection_status");Instant lease=instant(row.get("lease_until")),heartbeat=instant(row.get("last_heartbeat_at")),received=instant(row.get("received_at"));int attempts=((Number)row.get("attempt_count")).intValue();boolean expired=lease!=null&&!now.isBefore(lease);
   String state=state(string(row,"tournament_status"),match,job,string(row,"attempt_status"),received!=null,projection,Boolean.TRUE.equals(row.get("terminal_recovery")),row.get("gameplay_started_at")!=null,expired,attempts);
   Integer queuePosition=Set.of("QUEUED","RETRYING").contains(state)?++position:null;
   String workerStatus=string(row,"worker_status");if(workerStatus!=null&&(heartbeat==null||!heartbeat.isAfter(now.minusSeconds(30))))workerStatus="OFFLINE";
   String category="ERROR".equals(state)?row.get("projection_error")!=null?"RESULT_PROJECTION":Boolean.TRUE.equals(row.get("terminal_recovery"))?"RECOVERY_EXHAUSTED":"MATCH_ERROR":"RECOVERING".equals(state)?"LEASE_EXPIRED":"RETRYING".equals(state)?row.get("recovery_reason")!=null?"INFRASTRUCTURE_RECOVERY":"PRE_START_REASSIGNMENT":null;
   var item=new Item((UUID)row.get("match_id"),(UUID)row.get("job_id"),(UUID)row.get("execution_id"),match,state,string(row,"worker_name"),workerStatus,attempts,queuePosition,instant(row.get("created_at")),instant(row.get("claimed_at")),instant(row.get("started_at")),instant(row.get("gameplay_started_at")),heartbeat,lease,"LEASED".equals(job)?expired?"EXPIRED":"ACTIVE":"NONE",received,projection,category);
   counts.merge(state,1L,Long::sum);if(filter==null||state.equals(filter))items.add(item);
  }
  long total=items.size();int from=Math.min(items.size(),page*size),to=Math.min(items.size(),from+size);
  return new Page(id,revision,now,page,size,total,Map.copyOf(counts),List.copyOf(matches==null?items.subList(from,to):items),"Thứ tự tạo job đang chờ trong giải, không phải vị trí hàng đợi toàn hệ thống, ETA hoặc thứ tự worker sẽ claim. STARTING là đã nhận lệnh start; worker cũ chưa báo gameplay sẽ giữ trạng thái này.");
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> history(Authentication auth,String id,String match){
  var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){if(t.matches.stream().noneMatch(m->m.id.equals(match)))throw new AuthException("MATCH_NOT_FOUND",404);}
  var rows=db.queryForList("select a.id,a.status,a.claimed_at,a.started_at,a.gameplay_started_at,a.lease_until,w.name,r.received_at,r.projection_status,x.terminal from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id left join workers w on w.id=a.worker_id left join worker_result_receipts r on r.execution_id=a.id left join worker_execution_recoveries x on x.execution_id=a.id where j.tournament_id=? and j.match_id=? order by a.claimed_at,a.id",UUID.fromString(id),UUID.fromString(match));
  var result=new ArrayList<Map<String,Object>>();for(var row:rows){var item=new LinkedHashMap<String,Object>();item.put("executionId",row.get("id"));item.put("status",row.get("status"));item.put("workerDisplayName",row.get("name"));for(String key:List.of("claimed_at","started_at","gameplay_started_at","lease_until","received_at"))item.put(key,instant(row.get(key)));item.put("projectionStatus",row.get("projection_status"));item.put("terminalRecovery",row.get("terminal"));result.add(item);}return result;
 }
}
