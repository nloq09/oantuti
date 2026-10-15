package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
/** A dispute is an audited request to review a tournament match, not a second source of game results. */
@Service
public class AdminDisputeService {
    private final AdminCatalogService catalog;private final AdminDirectoryService query;private final AccountAuthorizationService accounts;
    private final TournamentService tournaments;private final OwnedTournamentService owned;private final TournamentAdministrationService administration;
    public AdminDisputeService(AdminCatalogService catalog,AdminDirectoryService query,AccountAuthorizationService accounts,TournamentService tournaments,OwnedTournamentService owned,TournamentAdministrationService administration){this.catalog=catalog;this.query=query;this.accounts=accounts;this.tournaments=tournaments;this.owned=owned;this.administration=administration;}
    private static final String SOURCE="""
        select e.id,e.tournament_id,e.target_id as match_id,e.sequence_number,e.reason,e.actor_user_id,u.username as created_by,t.name as tournament_name,e.occurred_at as created_at,
        case when exists(select 1 from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type='MATCH_REVIEW_REQUESTED') then 'RESOLVED'
        when exists(select 1 from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type='MATCH_REVIEW_REJECTED') then 'REJECTED'
        when exists(select 1 from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type in ('MATCH_REVIEW_CLOSED','RESULT_OVERRIDDEN','MATCH_RETRIED')) then 'RESOLVED'
        when exists(select 1 from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type='MATCH_REVIEW_ASSIGNED') then 'UNDER_REVIEW' else 'OPEN' end as status,
        (select a.actor_user_id from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type='MATCH_REVIEW_ASSIGNED' order by a.sequence_number desc limit 1) as assigned_referee
        from tournament_audit_events e join tournaments t on t.id=e.tournament_id left join users u on u.id=e.actor_user_id where e.event_type='MATCH_REVIEW_REQUESTED'
        """;
    public Map<String,Object> list(Authentication auth,Map<String,String> filters,int page,int size){
        accounts.requireAdmin(auth);owned.flush();var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.choice(clauses,args,filters,"status",Set.of("OPEN","UNDER_REVIEW","RESOLVED","REJECTED"));
        AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("reason","tournament_name","created_by"));AdminDirectoryService.dates(clauses,args,filters);
        if(AdminDirectoryService.nonempty(filters.get("tournament"))){clauses.add("tournament_id=?");args.add(AdminDirectoryService.uuid(filters.get("tournament")));}
        return catalog.page(SOURCE,clauses,args,page,size,"created_at desc,id");
    }
    public Map<String,Object> detail(Authentication auth,UUID id){
        accounts.requireAdmin(auth);owned.flush();var row=query.one("select * from ("+SOURCE+") disputes where id=?",id,"DISPUTE_NOT_FOUND");
        var t=tournaments.find(row.get("tournament_id").toString());var view=tournaments.view(t);String match=row.get("match_id").toString();
        row.put("revision",view.get("revision"));row.put("tournament_status",view.get("status"));row.put("tournament",view);
        row.put("match",((List<?>)view.get("matches")).stream().map(value->(Map<?,?>)value).filter(value->match.equals(value.get("id"))).findFirst().orElseThrow(()->new AuthException("MATCH_NOT_FOUND",404)));
        row.put("timeline",query.rows("select a.event_type,a.reason,a.occurred_at,u.username as actor from tournament_audit_events a left join users u on u.id=a.actor_user_id where a.tournament_id=? and a.target_id=? order by a.sequence_number desc limit 50",UUID.fromString(t.id),match));
        return row;
    }
    public Map<String,Object> decide(Authentication auth,UUID id,String decision,Long revision,String explanation,String winner,String participant){
        accounts.requireAdmin(auth);String reason=AdminDirectoryService.reason(explanation);var row=detail(auth,id);var t=tournaments.find(row.get("tournament_id").toString());String match=row.get("match_id").toString();
        synchronized(t){
            var latest=t.operations.audit().stream().filter(event->event.type().equals("MATCH_REVIEW_REQUESTED")&&match.equals(event.target())).reduce((a,b)->b).orElseThrow();
            if(!latest.id().equals(id)||!t.operations.reviews().containsKey(match))throw new AuthException("DISPUTE_ALREADY_CLOSED",409);
            switch(decision){case "assign"->administration.assignReview(auth,t.id,revision,match,reason);case "uphold"->administration.review(auth,t.id,revision,match,reason,true);case "reject"->administration.rejectReview(auth,t.id,revision,match,reason);case "retry"->administration.retry(auth,t.id,revision,match,reason);case "override"->administration.override(auth,t.id,revision,match,winner,reason);case "disqualify"->administration.disqualifyReview(auth,t.id,revision,match,participant,reason);default->throw new AuthException("INVALID_ACTION",400);}
        }return detail(auth,id);
    }
}
