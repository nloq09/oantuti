package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AdminAuditService {
    private final AdminCatalogService catalog;private final AccountAuthorizationService accounts;private final AuditRedactor redactor;
    public AdminAuditService(AdminCatalogService catalog,AccountAuthorizationService accounts,AuditRedactor redactor){this.catalog=catalog;this.accounts=accounts;this.redactor=redactor;}
    private static final String EVENTS="""
        select a.id,a.actor_user_id as actor_id,u.username as actor,u.role as actor_role,'CURRENT_ACCOUNT' as role_source,a.action,a.target_type as resource_type,cast(a.target_id as varchar(160)) as resource_id,a.reason,a.before_value,a.after_value,a.occurred_at as created_at,'SUCCESS' as result,cast(null as integer) as http_status,
        cast(null as varchar(80)) as tournament_name,coalesce(tu.username,b.name) as resource_name,cast(null as varchar(80)) as request_id
        from admin_audit_events a left join users u on u.id=a.actor_user_id left join users tu on tu.id=a.target_id and a.target_type='USER' left join bots b on b.id=a.target_id and a.target_type='BOT'
        union all
        select e.id,e.actor_user_id,u.username,u.role,case when e.actor_user_id is null then 'SYSTEM' else 'CURRENT_ACCOUNT' end,e.event_type,
        case when e.event_type like 'MATCH%' then 'MATCH' when e.event_type like 'PARTICIPANT%' or e.event_type like 'REGISTRATION_APPLICATION_%' then 'PARTICIPANT' when e.event_type like 'FINAL_VALIDATION%' or e.event_type like 'TIMELINE_BOT_VALIDATION%' or e.event_type like 'SUBMISSION%' or e.event_type like 'BOT_SUBMIT%' then 'SUBMISSION' else 'TOURNAMENT' end,
        e.target_id,e.reason,e.before_value,e.after_value,e.occurred_at,'SUCCESS',cast(null as integer),t.name,coalesce(case when v.id is not null then concat(coalesce(b.name,'Bot'),' v',cast(v.version_number as varchar(12)),' · ',su.username) else null end,tr.display_name,r.display_name,p.display_name,tu.username,case when e.target_id=cast(t.id as varchar(160)) then t.name else null end),cast(null as varchar(80))
        from tournament_audit_events e join tournaments t on t.id=e.tournament_id left join users u on u.id=e.actor_user_id
        left join tournament_timeline_registrations tr on cast(tr.id as varchar(160))=e.target_id and tr.tournament_id=e.tournament_id
        left join tournament_registrations r on cast(r.id as varchar(160))=e.target_id and r.tournament_id=e.tournament_id
        left join tournament_participants p on cast(p.id as varchar(160))=e.target_id and p.tournament_id=e.tournament_id
        left join users tu on cast(tu.id as varchar(160))=e.target_id
        left join tournament_bot_submissions s on cast(s.id as varchar(160))=e.target_id and s.tournament_id=e.tournament_id
        left join tournament_final_validations f on cast(f.id as varchar(160))=e.target_id and f.tournament_id=e.tournament_id
        left join tournament_submission_versions v on v.id=coalesce(s.version_id,f.version_id)
        left join users su on su.id=coalesce(s.user_id,f.user_id)
        left join bots b on b.id=v.execution_bot_id
        union all
        select e.id,e.actor_user_id,u.username,e.actor_role,'AT_REQUEST',e.action,e.resource_type,e.resource_id,e.reason,cast(null as varchar(32)),cast(null as varchar(32)),e.occurred_at,e.result,e.http_status,t.name,coalesce(tu.username,b.name,t.name),cast(null as varchar(80))
        from admin_operation_events e left join users u on u.id=e.actor_user_id
        left join users tu on cast(tu.id as varchar(160))=e.resource_id and e.resource_type='USER'
        left join bots b on cast(b.id as varchar(160))=e.resource_id and e.resource_type='BOT'
        left join tournaments t on cast(t.id as varchar(160))=e.resource_id and e.resource_type='TOURNAMENT'
        union all
        select e.id,e.actor_user_id,u.username,u.role,case when e.actor_user_id is null then 'WORKER' else 'CURRENT_ACCOUNT' end,e.event_type,case when e.event_type='TOURNAMENT_WORKER_POLICY' then 'TOURNAMENT' when e.event_type like 'REGISTRATION_TOKEN%' then 'WORKER_TOKEN' when e.event_type like 'WORKER_GROUP%' then 'WORKER_GROUP' else 'WORKER' end,cast(e.resource_id as varchar(160)),e.reason,e.before_value,e.after_value,e.occurred_at,'SUCCESS',cast(null as integer),cast(null as varchar(80)),coalesce(w.name,g.name,t.name),cast(null as varchar(80))
        from worker_identity_events e left join users u on u.id=e.actor_user_id left join workers w on w.id=e.resource_id left join worker_groups g on g.id=e.resource_id left join tournaments t on t.id=e.resource_id
        """;
    private static final String SOURCE="select events.*,case when action like 'WORKER%' or action like 'REGISTRATION_TOKEN%' or action like 'REMOTE_%' or action='TOURNAMENT_WORKER_POLICY' then 'WORKER' when action like 'FINAL_VALIDATION%' or action like 'TIMELINE_BOT_VALIDATION%' then 'VALIDATION' when action like 'PARTICIPANT%' or action like 'REGISTRATION%' or action like 'CHECK_IN%' or action like 'WAITLIST_%' then 'REGISTRATION' when action like 'BOT_%' or action like 'SUBMISSION%' or action like 'CODING_DEADLINE%' or action like 'EMERGENCY_SUBMISSION%' or resource_type='BOT' then 'BOT' when action like '%REQUEST' or resource_type='SECURITY' then 'SECURITY' when resource_type='USER' then 'ACCOUNT' when action like 'MATCH%' or action like 'RESULT_%' or resource_type in ('MATCH','REPLAY','DISPUTE') then 'MATCH' when resource_type='TOURNAMENT' then 'TOURNAMENT' else 'SYSTEM' end as category,case when http_status in (401,403) then 'DENIED' when http_status=409 then 'CONFLICT' when result='FAILURE' then 'FAILED' else result end as outcome from ("+EVENTS+") events";
    public Map<String,Object> list(Authentication auth,Map<String,String> filters,int page,int size){
        accounts.requireAdmin(auth);var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("actor","action","resource_name","tournament_name","resource_id","reason"));AdminDirectoryService.dates(clauses,args,filters);
        if(Set.of("FAILED","DENIED","CONFLICT").contains(filters.getOrDefault("result",""))){clauses.add("outcome=?");args.add(filters.get("result"));}else AdminDirectoryService.choice(clauses,args,filters,"result",Set.of("SUCCESS","FAILURE"));
        AdminDirectoryService.choice(clauses,args,filters,"category",Set.of("ACCOUNT","TOURNAMENT","REGISTRATION","BOT","VALIDATION","MATCH","WORKER","SECURITY","SYSTEM"));
        if(AdminDirectoryService.nonempty(filters.get("actor_kind"))){String kind=filters.get("actor_kind");if(kind.equals("HUMAN"))clauses.add("actor_id is not null");else if(Set.of("SYSTEM","WORKER","SCHEDULER").contains(kind)){clauses.add("role_source=?");args.add(kind);}else throw new com.rpsbattle.auth.AuthException("INVALID_FILTER",400);}
        for(String key:List.of("resource_id","request_id"))if(AdminDirectoryService.nonempty(filters.get(key))){if(filters.get(key).length()>160)throw new com.rpsbattle.auth.AuthException("INVALID_FILTER",400);clauses.add(key+"=?");args.add(filters.get(key));}
        if(AdminDirectoryService.nonempty(filters.get("http_status"))){try{int status=Integer.parseInt(filters.get("http_status"));if(status<100||status>599)throw new NumberFormatException();clauses.add("http_status=?");args.add(status);}catch(NumberFormatException invalid){throw new com.rpsbattle.auth.AuthException("INVALID_FILTER",400);}}
        AdminDirectoryService.choice(clauses,args,filters,"resource_type",Set.of("USER","BOT","MATCH","TOURNAMENT","PARTICIPANT","DISPUTE","REPLAY","SYSTEM","AUDIT","DASHBOARD","OTHER","WORKER","WORKER_TOKEN","WORKER_GROUP","SUBMISSION","SECURITY"));
        if(AdminDirectoryService.nonempty(filters.get("actor"))){clauses.add("actor_id=?");args.add(AdminDirectoryService.uuid(filters.get("actor")));}
        if(AdminDirectoryService.nonempty(filters.get("action"))){if(filters.get("action").length()>80)throw new com.rpsbattle.auth.AuthException("INVALID_FILTER",400);clauses.add("action=?");args.add(filters.get("action"));}
        var result=new LinkedHashMap<>(catalog.page(SOURCE,clauses,args,page,size,"created_at desc,id"));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)result.get("items");
        result.put("items",rows.stream().map(row->{var safe=new LinkedHashMap<>(row);for(String key:List.of("before_value","after_value","reason"))safe.put(key,redactor.redact(row.get(key)));return safe;}).toList());return result;
    }
}
