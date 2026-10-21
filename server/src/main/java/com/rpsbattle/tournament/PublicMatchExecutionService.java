package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Public execution labels only. Worker identity, leases, receipts and attempts stay in scoped monitors. */
@Service
public class PublicMatchExecutionService {
 private final JdbcTemplate db;private final Clock clock;
 public PublicMatchExecutionService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
 private static UUID uuid(String value){if(value==null)return null;try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){return null;}}
 public List<TournamentMatch> active(Tournament t,int limit){
  var id=uuid(t.id);if(id==null)return t.matches.stream().filter(m->m.status==TournamentMatch.Status.RUNNING).limit(limit).toList();
  var ids=new HashSet<>(db.queryForList("select j.match_id from match_execution_jobs j join tournament_matches m on m.id=j.match_id and m.tournament_id=j.tournament_id where j.tournament_id=? and j.status<>'CANCELLED' and m.status in ('PENDING','RUNNING') order by j.created_at,j.id limit ?",UUID.class,id,limit));
  return t.matches.stream().filter(m->m.status==TournamentMatch.Status.RUNNING||m.status==TournamentMatch.Status.PENDING&&uuid(m.id)!=null&&ids.contains(uuid(m.id))).sorted(Comparator.comparingInt((TournamentMatch m)->m.status==TournamentMatch.Status.RUNNING?0:1).thenComparingInt(m->m.round).thenComparing(m->m.id)).limit(limit).toList();
 }
 public void decorate(Tournament t,List<Map<String,Object>> matches){
  var ids=matches.stream().map(m->uuid((String)m.get("id"))).filter(Objects::nonNull).toList();var facts=new HashMap<String,Map<String,Object>>();
  if(uuid(t.id)!=null&&!ids.isEmpty()){
   // Batch even the legacy full-detail endpoint; paged callers normally request at most 50 IDs.
   for(int from=0;from<ids.size();from+=500){var batch=ids.subList(from,Math.min(ids.size(),from+500));var args=new ArrayList<Object>();args.add(uuid(t.id));args.addAll(batch);
    var rows=db.queryForList("select j.match_id,j.status as job_status,a.status as attempt_status,a.gameplay_started_at,a.lease_until,r.received_at,r.projection_status,x.terminal as terminal_recovery,(select count(*) from match_execution_attempts n where n.job_id=j.id) as attempt_count from match_execution_jobs j left join match_execution_attempts a on a.job_id=j.id and (a.active_marker=1 or (not exists(select 1 from match_execution_attempts active where active.job_id=j.id and active.active_marker=1) and not exists(select 1 from match_execution_attempts newer where newer.job_id=j.id and (newer.claimed_at>a.claimed_at or (newer.claimed_at=a.claimed_at and newer.id>a.id))))) left join worker_result_receipts r on r.execution_id=a.id and r.tournament_id=j.tournament_id left join worker_execution_recoveries x on x.execution_id=a.id and x.tournament_id=j.tournament_id where j.tournament_id=? and j.match_id in ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")",args.toArray());
    for(var row:rows)facts.put(row.get("match_id").toString(),row);
   }
  }
  var now=clock.instant();for(var match:matches){var fact=facts.get(match.get("id"));String state=localState(t.status,match.get("status").toString());
   if(fact!=null){var lease=RemoteExecutionReadService.instant(fact.get("lease_until"));state=RemoteExecutionReadService.state(t.status.name(),match.get("status").toString(),RemoteExecutionReadService.string(fact,"job_status"),RemoteExecutionReadService.string(fact,"attempt_status"),fact.get("received_at")!=null,RemoteExecutionReadService.string(fact,"projection_status"),Boolean.TRUE.equals(fact.get("terminal_recovery")),fact.get("gameplay_started_at")!=null,lease!=null&&!now.isBefore(lease),((Number)fact.get("attempt_count")).intValue());match.put("onlineMatchId",null);}
   match.put("remote",fact!=null);match.put("executionState",state);match.put("reviewRequired",t.operations.reviews().containsKey(match.get("id")));
  }
 }
 static String localState(TournamentStatus tournament,String match){if(match.equals("FINISHED"))return "FINISHED";if(tournament==TournamentStatus.CANCELLED)return "CANCELLED";return switch(match){case "RUNNING"->"RUNNING_LOCAL";case "FAILED"->"ERROR";default->"WAITING_OPPONENT";};}
}
