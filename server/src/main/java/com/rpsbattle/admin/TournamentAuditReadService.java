package com.rpsbattle.admin;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Scoped organizer read model; never changes or deletes original events. */
@Service
public class TournamentAuditReadService {
    private final TournamentService tournaments;
    private final TournamentAuthorizationService authorization;
    private final AdminDirectoryService query;
    private final JdbcTemplate db;
    private final AuditRedactor redactor;
    public TournamentAuditReadService(TournamentService tournaments,TournamentAuthorizationService authorization,AdminDirectoryService query,JdbcTemplate db,AuditRedactor redactor){this.tournaments=tournaments;this.authorization=authorization;this.query=query;this.db=db;this.redactor=redactor;}

    private static final String CTE="""
        with enriched as (
          select e.id,e.sequence_number,e.event_type as action,e.actor_user_id as actor_id,u.username as actor,
          case when e.actor_user_id is not null then 'CURRENT_ACCOUNT' when e.reason='REMOTE_WORKER_RESULT' then 'WORKER' else 'SYSTEM' end as role_source,
          e.target_id as resource_id,e.reason,e.before_value,e.after_value,e.occurred_at as created_at,
          coalesce(pr.username,lr.username,pu.username,tu.username,vu.username) as target_user,
          coalesce(tr.display_name,r.display_name,p.display_name,tu.username,vu.username) as resource_name,
          w.language as bot_language,v.version_number as bot_version,f.user_id as validation_user,
          case when f.id is not null then cast(f.id as varchar(160)) else null end as correlation_id,
          case when e.event_type like 'FINAL_VALIDATION_%' or e.event_type like 'TIMELINE_BOT_VALIDATION_%' or e.event_type='TIMELINE_VALIDATION_STARTED' then 'VALIDATION'
               when e.event_type like '%CHECK_IN%' or e.event_type='CHECK_IN' then 'CHECK_IN'
               when e.event_type like 'RESULT_%' or e.event_type='SERIES_UPDATED' then 'RESULT'
               when e.event_type like 'MATCH%' or e.event_type like 'ROUND_%' then 'MATCH'
               when e.event_type like 'TIMELINE_CODING%' or e.event_type like 'TIMELINE_OVERRIDE_%CODING%' then 'CODING'
               when e.event_type like 'SUBMISSION%' or e.event_type like 'BOT_SUBMIT%' or e.event_type like 'BOT_AUTO%' or e.event_type like 'CODING_DEADLINE%' or e.event_type like 'EMERGENCY_SUBMISSION%' then 'SUBMISSION'
               when e.event_type like 'PARTICIPANT%' or e.event_type like 'REGISTRATION%' or e.event_type like 'WAITLIST%' or e.event_type like 'TIMELINE_REGISTRATION%' then 'REGISTRATION'
               when e.event_type like 'TIMELINE_OVERRIDE%' or e.event_type='RESULT_OVERRIDDEN' then 'ADMIN'
               when e.event_type='PERSISTENCE_ERROR' then 'SYSTEM' else 'TOURNAMENT' end as category,
          case when e.event_type like 'TIMELINE_CODING_%MIN_LEFT' or e.event_type in ('TIMELINE_CODING_STARTING','TIMELINE_REGISTRATION_CLOSING','TIMELINE_CHECK_IN_CLOSING','TIMELINE_TOURNAMENT_STARTING','NOTIFICATIONS_READ') then 'ACTIVITY' else 'AUDIT' end as event_kind,
          case when e.event_type like '%INFRA_ERROR' or e.event_type in ('MATCH_ERROR','PERSISTENCE_ERROR') then 'INFRA_ERROR'
               when e.event_type like '%FAILED' then 'FAILED'
               when e.event_type like '%PASSED' then 'PASSED'
               when e.event_type like '%REJECTED' or (e.event_type='PARTICIPANT_APPLICATION_REVIEWED' and e.after_value='REJECTED') then 'REJECTED'
               else 'SUCCESS' end as outcome
          from tournament_audit_events e left join users u on u.id=e.actor_user_id
          left join tournament_timeline_registrations tr on cast(tr.id as varchar(160))=e.target_id and tr.tournament_id=e.tournament_id
          left join users pr on pr.id=tr.user_id
          left join tournament_registrations r on cast(r.id as varchar(160))=e.target_id and r.tournament_id=e.tournament_id
          left join users lr on lr.id=r.user_id
          left join tournament_participants p on cast(p.id as varchar(160))=e.target_id and p.tournament_id=e.tournament_id
          left join users pu on pu.id=p.owner_user_id
          left join users tu on cast(tu.id as varchar(160))=e.target_id
          left join tournament_final_validations f on cast(f.id as varchar(160))=e.target_id and f.tournament_id=e.tournament_id
          left join tournament_bot_submissions s on cast(s.id as varchar(160))=e.target_id and s.tournament_id=e.tournament_id
          left join tournament_submission_versions v on v.id=coalesce(f.version_id,s.version_id)
          left join tournament_code_workspaces w on w.id=v.workspace_id
          left join users vu on vu.id=coalesce(f.user_id,s.user_id)
          where e.tournament_id=?
        ), paired as (
          select e.*,peer.id as merged_id,peer.action as merged_action,peer.sequence_number as merged_sequence,
                 peer.resource_id as merged_resource_id,peer.created_at as merged_at,
                 peer.before_value as merged_before,peer.after_value as merged_after,peer.reason as merged_reason
          from enriched e left join enriched peer on e.action like 'FINAL_VALIDATION_%'
            and peer.action=replace(e.action,'FINAL_VALIDATION_','TIMELINE_BOT_VALIDATION_')
            and e.validation_user is not null and peer.resource_id=cast(e.validation_user as varchar(160))
            and coalesce(e.after_value,'')=coalesce(peer.after_value,'') and e.actor_id is null and peer.actor_id is null and e.sequence_number=peer.sequence_number+1
            and peer.created_at<=e.created_at and peer.created_at>=e.created_at-interval '2' second
        ), visible as (
          select * from paired e where not exists(select 1 from paired owner where owner.merged_id=e.id)
        )
        """;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(Authentication auth,String id,Map<String,String> filters,int page,int size){
        authorization.requireManager(auth,tournaments.find(id));
        UUID tournament;try{tournament=UUID.fromString(id);}catch(RuntimeException invalid){throw new AuthException("INVALID_FILTER",400);}
        if(page<0||page>100000||!Set.of(25,50,100).contains(size))throw new AuthException("INVALID_PAGE",400);
        var clauses=new ArrayList<String>();var args=new ArrayList<Object>();args.add(tournament);
        AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("actor","target_user","resource_name","action","resource_id","bot_language","merged_action"));
        AdminDirectoryService.dates(clauses,args,filters);
        AdminDirectoryService.choice(clauses,args,filters,"category",Set.of("REGISTRATION","CODING","SUBMISSION","VALIDATION","CHECK_IN","MATCH","RESULT","TOURNAMENT","ADMIN","SYSTEM"));
        AdminDirectoryService.choice(clauses,args,filters,"outcome",Set.of("SUCCESS","PASSED","FAILED","INFRA_ERROR","REJECTED"));
        String kind=filters.getOrDefault("kind","IMPORTANT");
        if(kind.equals("IMPORTANT"))clauses.add("event_kind='AUDIT'");else if(kind.equals("ACTIVITY"))clauses.add("event_kind='ACTIVITY'");else if(!kind.equals("ALL"))throw new AuthException("INVALID_FILTER",400);
        String actor=filters.getOrDefault("actor_kind","");
        if(actor.equals("HUMAN"))clauses.add("actor_id is not null");else if(Set.of("SYSTEM","WORKER","SCHEDULER").contains(actor)){clauses.add("role_source=?");args.add(actor);}else if(!actor.isEmpty())throw new AuthException("INVALID_FILTER",400);
        for(String key:List.of("action","resource_id","correlation_id","audit_id")){String value=filters.get(key);if(AdminDirectoryService.nonempty(value)){if(value.length()>160)throw new AuthException("INVALID_FILTER",400);String column=key.equals("audit_id")?"cast(id as varchar(160))":key,peer=switch(key){case "action"->"merged_action";case "resource_id"->"merged_resource_id";case "audit_id"->"cast(merged_id as varchar(160))";default->null;};clauses.add(peer==null?column+"=?":"("+column+"=? or "+peer+"=?)");args.add(value);if(peer!=null)args.add(value);}}
        String where=clauses.isEmpty()?"":" where "+String.join(" and ",clauses),from=" from visible"+where;
        long total=Objects.requireNonNull(db.queryForObject(CTE+"select count(*)"+from,Long.class,args.toArray()));
        args.add(size);args.add((long)page*size);
        var items=query.rows(CTE+"select *"+from+" order by created_at desc,sequence_number desc,id limit ? offset ?",args.toArray());
        for(var row:items){for(String key:List.of("before_value","after_value","reason","merged_before","merged_after","merged_reason"))row.put(key,redactor.redact(row.get(key)));}
        return Map.of("items",items,"total",total,"page",page,"size",size);
    }
}
