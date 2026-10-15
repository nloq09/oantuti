package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AdminAuditService {
    private final AdminCatalogService catalog;private final AccountAuthorizationService accounts;
    public AdminAuditService(AdminCatalogService catalog,AccountAuthorizationService accounts){this.catalog=catalog;this.accounts=accounts;}
    private static final String SOURCE="""
        select a.id,a.actor_user_id as actor_id,u.username as actor,u.role as actor_role,'CURRENT_ACCOUNT' as role_source,a.action,a.target_type as resource_type,cast(a.target_id as varchar(160)) as resource_id,a.reason,a.before_value,a.after_value,a.occurred_at as created_at,'SUCCESS' as result,cast(null as integer) as http_status
        from admin_audit_events a left join users u on u.id=a.actor_user_id
        union all
        select e.id,e.actor_user_id,u.username,u.role,'CURRENT_ACCOUNT',e.event_type,case when e.event_type like 'MATCH%' then 'MATCH' when e.event_type like 'PARTICIPANT%' or e.event_type like 'REGISTRATION%' then 'PARTICIPANT' else 'TOURNAMENT' end,e.target_id,e.reason,e.before_value,e.after_value,e.occurred_at,'SUCCESS',cast(null as integer)
        from tournament_audit_events e left join users u on u.id=e.actor_user_id
        union all
        select e.id,e.actor_user_id,u.username,e.actor_role,'AT_REQUEST',e.action,e.resource_type,e.resource_id,e.reason,cast(null as varchar(32)),cast(null as varchar(32)),e.occurred_at,e.result,e.http_status
        from admin_operation_events e left join users u on u.id=e.actor_user_id
        """;
    public Map<String,Object> list(Authentication auth,Map<String,String> filters,int page,int size){
        accounts.requireAdmin(auth);var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("actor","action","resource_id","reason"));AdminDirectoryService.dates(clauses,args,filters);
        AdminDirectoryService.choice(clauses,args,filters,"result",Set.of("SUCCESS","FAILURE"));
        AdminDirectoryService.choice(clauses,args,filters,"resource_type",Set.of("USER","BOT","MATCH","TOURNAMENT","PARTICIPANT","DISPUTE","REPLAY","SYSTEM","AUDIT","DASHBOARD","OTHER"));
        if(AdminDirectoryService.nonempty(filters.get("actor"))){clauses.add("actor_id=?");args.add(AdminDirectoryService.uuid(filters.get("actor")));}
        if(AdminDirectoryService.nonempty(filters.get("action"))){if(filters.get("action").length()>80)throw new com.rpsbattle.auth.AuthException("INVALID_FILTER",400);clauses.add("action=?");args.add(filters.get("action"));}
        return catalog.page(SOURCE,clauses,args,page,size,"created_at desc,id");
    }
}
