package com.rpsbattle.tournament.coding;

import com.rpsbattle.auth.AuthException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** One scalar row per approved person. Never selects source, logs or hidden test payloads. */
@Repository
public class SubmissionDashboardStore {
 private final NamedParameterJdbcTemplate jdbc;
 public SubmissionDashboardStore(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final Map<String,String> FILTERS=Map.of(
  "ALL","true", "NO_WORKSPACE","workspace_count=0", "NOT_SUBMITTED","not submitted",
  "NOT_READY","not ready", "READY","ready", "BUILD_FAILED","build_failed",
  "LOCKED","(freeze_state='LOCKED' or submission_state='LOCKED')", "NO_VALID_SUBMISSION","freeze_state<>'LOCKED'");
 private static final String FACTS="""
 with work as (
  select w.user_id,count(distinct w.id) workspace_count,
   max(w.updated_at) updated_at,
   max(case when w.status='BUILDING' then 1 else 0 end) building,
   max(case when w.status='TESTING' then 1 else 0 end) testing,
   max(case when w.status='BUILD_FAILED' then 1 else 0 end) failed,
   max(case when w.status='TEST_FAILED' then 1 else 0 end) test_failed,
   max(case when w.status='BUILT' then 1 else 0 end) built
  from tournament_code_workspaces w where w.tournament_id=:t group by w.user_id
 ), ready_users as (
  select distinct w.user_id from tournament_code_workspaces w join tournament_submission_versions v on v.workspace_id=w.id
  where w.tournament_id=:t and v.status in ('READY','SUBMITTED','LOCKED') and v.public_passed=true and v.sample_passed=true
 ), languages as (
  select user_id,string_agg(language, ', ' order by language) language from tournament_code_workspaces where tournament_id=:t group by user_id
 ), facts as (
  select r.user_id,u.username,u.display_name,coalesce(l.language,'—') language,
   coalesce(w.workspace_count,0) workspace_count,
   case when w.user_id is null then 'NO_WORKSPACE' when w.building=1 then 'BUILDING' when w.testing=1 then 'TESTING' when ready.user_id is not null then 'READY' when w.failed=1 then 'BUILD_FAILED' when w.test_failed=1 then 'TEST_FAILED' when w.built=1 then 'BUILT' else 'DRAFT' end workspace_state,
   s.id submission_id,s.status submission_state,s.submitted_at,
   coalesce(s.status in ('SUBMITTED','LOCKED'),false) submitted,
   (ready.user_id is not null) ready,
   (coalesce(w.failed,0)=1 and ready.user_id is null) build_failed,
   (coalesce(w.test_failed,0)=1 and ready.user_id is null) test_failed,
   case when f.id is null then 'PENDING' when f.decision='MISSING' then 'MISSING' else 'LOCKED' end freeze_state,
   f.reason freeze_reason,f.version_number frozen_version,f.language frozen_language,
   v.status validation_state,
   greatest(w.updated_at,s.submitted_at,f.frozen_at) updated_at
  from tournament_timeline_registrations r join users u on u.id=r.user_id
  left join work w on w.user_id=r.user_id left join languages l on l.user_id=r.user_id
  left join ready_users ready on ready.user_id=r.user_id
  left join tournament_bot_submissions s on s.tournament_id=r.tournament_id and s.user_id=r.user_id
  left join tournament_coding_freezes f on f.tournament_id=r.tournament_id and f.user_id=r.user_id and f.coding_close_at=:cutoff
  left join tournament_final_validations v on v.freeze_id=f.id
  where r.tournament_id=:t and r.status='APPROVED'
 )
 """;
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> read(String id,Instant cutoff,int page,int size,String filter,String search){
  if(page<0||page>10000||size<1||size>100||!FILTERS.containsKey(filter)||search==null||search.length()>80)throw new AuthException("INVALID_SUBMISSION_FILTER",400);
  var args=new HashMap<String,Object>();args.put("t",UUID.fromString(id));args.put("cutoff",java.sql.Timestamp.from(cutoff));args.put("q",search.strip().toLowerCase(Locale.ROOT));args.put("limit",size);args.put("offset",page*size);
  String where=" where (position(:q in lower(username))>0 or position(:q in lower(display_name))>0) and ("+FILTERS.get(filter)+")";
  var stats=jdbc.queryForMap(FACTS+"""
   select count(*) approved,
    count(case when workspace_count>0 then 1 end) workspace_users,
    count(case when submitted then 1 end) submitted,
    count(case when ready then 1 end) ready,
    count(case when build_failed then 1 end) build_failed,
    count(case when test_failed then 1 end) test_failed,
    count(case when workspace_count=0 then 1 end) no_workspace,
    count(case when submitted and not ready then 1 end) submitted_not_ready,
    count(case when submission_state in ('REJECTED','DISQUALIFIED') then 1 end) rejected,
    count(case when freeze_state='LOCKED' then 1 end) frozen,
    count(case when freeze_state='MISSING' then 1 end) freeze_missing,
    count(case when freeze_state='PENDING' then 1 end) freeze_pending,
    count(case when validation_state='PASSED' then 1 end) validation_passed
   from facts
   """,args);
  long total=jdbc.queryForObject(FACTS+"select count(*) from facts"+where,args,Long.class);
  var items=jdbc.queryForList(FACTS+"select * from facts"+where+" order by lower(display_name),user_id limit :limit offset :offset",args);
  var jobs=jdbc.queryForMap("""
   select count(case when j.status in ('QUEUED','RUNNING') then 1 end) active,
    count(case when j.status='FAILED' then 1 end) failed
   from bot_coding_jobs j join tournament_code_workspaces w on w.id=j.workspace_id where w.tournament_id=:t
   """,args);
  return Map.of("items",items,"total",total,"page",page,"size",size,"filter",filter,"summary",stats,"jobs",jobs);
 }
}
