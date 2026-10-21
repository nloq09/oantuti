package com.rpsbattle.tournament.cleanup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Repository
public class TournamentCleanupStore {
 private final JdbcTemplate db;
 public TournamentCleanupStore(JdbcTemplate db){this.db=db;}
 public record State(UUID id,Instant candidateAt,long revision,String reason,Instant trashedAt,Instant protectedUntil){}
 public State state(UUID id){return db.query("select * from tournament_cleanup where tournament_id=?",(r,n)->new State(id,instant(r.getTimestamp("candidate_at")),r.getLong("candidate_revision"),r.getString("reason"),instant(r.getTimestamp("trashed_at")),instant(r.getTimestamp("protected_until"))),id).stream().findFirst().orElse(null);}
 private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
 public boolean hasReferences(UUID id){
  return Boolean.TRUE.equals(db.queryForObject("""
   select exists(select 1 from tournament_participants where tournament_id=?)
   or exists(select 1 from tournament_matches where tournament_id=?)
   or exists(select 1 from tournament_registrations where tournament_id=?)
   or exists(select 1 from tournament_timeline_registrations where tournament_id=?)
   or exists(select 1 from tournament_code_workspaces where tournament_id=?)
   or exists(select 1 from tournament_bot_submissions where tournament_id=?)
   """,Boolean.class,id,id,id,id,id,id));
 }
 public void clear(UUID id){db.update("update tournament_cleanup set candidate_at=null,candidate_revision=0,reason=null where tournament_id=? and trashed_at is null",id);}
 @Transactional public boolean mark(UUID id,long revision,Instant now,String reason){
  var rows=db.queryForList("select revision,visibility,trashed_at from tournaments where id=? for update",id);
  if(rows.isEmpty()||!"PUBLIC".equals(rows.getFirst().get("visibility"))||rows.getFirst().get("trashed_at")!=null||((Number)rows.getFirst().get("revision")).longValue()!=revision||hasReferences(id))return false;
  var state=state(id);if(state!=null&&state.protectedUntil()!=null&&state.protectedUntil().isAfter(now))return false;
  if(state==null)db.update("insert into tournament_cleanup(tournament_id,candidate_at,candidate_revision,reason) values(?,?,?,?)",id,Timestamp.from(now),revision,reason);
  else db.update("update tournament_cleanup set candidate_at=?,candidate_revision=?,reason=? where tournament_id=?",Timestamp.from(now),revision,reason,id);
  return true;
 }
 @Transactional public boolean trash(UUID id,long revision,Instant candidateAt,Instant now){
  db.queryForList("select id from tournaments where id=? for update",id);
  var state=state(id);if(state==null||state.trashedAt()!=null||!Objects.equals(candidateAt,state.candidateAt())||state.candidateAt().plus(TournamentCleanupPolicy.GRACE).isAfter(now)||hasReferences(id))return false;
  if(db.update("update tournaments set trashed_at=? where id=? and trashed_at is null and visibility='PUBLIC' and status in ('DRAFT','CANCELLED') and started_at is null and revision=?",Timestamp.from(now),id,revision)!=1)return false;
  db.update("update tournament_cleanup set trashed_at=? where tournament_id=?",Timestamp.from(now),id);return true;
 }
 @Transactional public void restore(UUID id,Instant now){
  db.queryForList("select id from tournaments where id=? for update",id);
  if(db.update("update tournaments set trashed_at=null where id=? and visibility='PUBLIC' and trashed_at is not null",id)!=1)throw new com.rpsbattle.auth.AuthException("TOURNAMENT_NOT_IN_TRASH",409);
  db.update("update tournament_cleanup set candidate_at=null,candidate_revision=0,reason=null,trashed_at=null,protected_until=? where tournament_id=?",Timestamp.from(now.plus(TournamentCleanupPolicy.DRAFT_IDLE)),id);
 }
 public void retain(UUID id,Instant now){db.update("update tournament_cleanup set candidate_at=null,candidate_revision=0,reason=null,protected_until=? where tournament_id=? and trashed_at is null",Timestamp.from(now.plus(TournamentCleanupPolicy.DRAFT_IDLE)),id);}
 public List<Map<String,Object>> list(){return db.queryForList("select t.id,t.name,t.status,u.username as owner,c.candidate_at,c.candidate_revision,c.reason,c.trashed_at,c.protected_until from tournament_cleanup c join tournaments t on t.id=c.tournament_id join users u on u.id=t.owner_user_id where c.candidate_at is not null or c.trashed_at is not null order by c.candidate_at,t.id limit 100");}
}
