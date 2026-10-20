package com.rpsbattle.tournament;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Scalar projections only: never load sources, logs, hidden tests or worker credentials. */
@Repository
public class OrganizerMetricsStore {
 @PersistenceContext EntityManager db;
 private final JdbcTemplate jdbc;
 public OrganizerMetricsStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Workspace(UUID user,String state,String versionState){}
 public record Validation(UUID user,String state,boolean applied,Instant emergencyDeadline){}
 public record Workers(long serving,long online,long busy,long degraded,long offline,Long availableSlots,String capacityNote){}
 public record Snapshot(Map<UUID,String> applications,List<Workspace> workspaces,Map<UUID,String> submissions,List<Validation> validations,Workers workers){}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Snapshot read(UUID id,Instant now){
  var applications=new HashMap<UUID,String>();for(var row:db.createQuery("select r.userId,r.status from TimelineRegistration r where r.tournamentId=:t",Object[].class).setParameter("t",id).getResultList())applications.put((UUID)row[0],(String)row[1]);
  var workspaces=db.createQuery("select w.userId,w.status,v.status from CodeWorkspace w left join CodeVersion v on v.id=w.currentVersionId where w.tournamentId=:t",Object[].class).setParameter("t",id).getResultStream().map(r->new Workspace((UUID)r[0],(String)r[1],(String)r[2])).toList();
  var submissions=new HashMap<UUID,String>();for(var row:db.createQuery("select s.userId,s.status from BotSubmission s where s.tournamentId=:t",Object[].class).setParameter("t",id).getResultList())submissions.put((UUID)row[0],(String)row[1]);
  var validations=db.createQuery("select f.userId,f.status,f.appliedAt,f.emergencyDeadline from FinalValidation f,CodingFreeze c where f.freezeId=c.id and f.tournamentId=:t and f.historyJson not like '%TIMELINE_EXTENDED%' and not exists(select n.id from CodingFreeze n where n.tournamentId=f.tournamentId and n.userId=f.userId and n.codingCloseAt>c.codingCloseAt)",Object[].class).setParameter("t",id).getResultStream().map(r->new Validation((UUID)r[0],(String)r[1],r[2]!=null,(Instant)r[3])).toList();
  long[] states=new long[5];jdbc.query("select w.status,w.last_heartbeat_at from workers w where exists(select 1 from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.worker_id=w.id and a.active_marker=1 and j.tournament_id=?)",r->{states[0]++;var heartbeat=r.getTimestamp(2);String status=r.getString(1);if(heartbeat==null||!heartbeat.toInstant().isAfter(now.minusSeconds(30))||Set.of("OFFLINE","DISABLED").contains(status))states[4]++;else if(status.equals("ONLINE"))states[1]++;else if(status.equals("BUSY"))states[2]++;else states[3]++;},id);
  return new Snapshot(Map.copyOf(applications),workspaces,Map.copyOf(submissions),validations,new Workers(states[0],states[1],states[2],states[3],states[4],null,"Chỉ đếm máy đang giữ assignment của giải. Slot dùng chung chưa có phân bổ riêng cho giải."));
 }
}
