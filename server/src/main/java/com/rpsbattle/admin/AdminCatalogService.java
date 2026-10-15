package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AdminCatalogService {
    private final JdbcTemplate db;private final AdminDirectoryService query;private final AccountAuthorizationService accounts;
    private final TournamentService tournaments;private final OwnedTournamentService owned;private final TournamentAdministrationService administration;
    public AdminCatalogService(JdbcTemplate db,AdminDirectoryService query,AccountAuthorizationService accounts,TournamentService tournaments,OwnedTournamentService owned,TournamentAdministrationService administration){this.db=db;this.query=query;this.accounts=accounts;this.tournaments=tournaments;this.owned=owned;this.administration=administration;}
    public Map<String,Object> page(String source,List<String> conditions,List<Object> values,int page,int size,String order){
        if(page<0||page>100000||size<1||size>100)throw new AuthException("INVALID_PAGE",400);
        var args=new ArrayList<>(values);String from=" from ("+source+") directory"+(conditions.isEmpty()?"":" where "+String.join(" and ",conditions));
        var total=db.queryForObject("select count(*)"+from,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return Map.of("items",query.rows("select *"+from+" order by "+order+" limit ? offset ?",args.toArray()),"total",Objects.requireNonNull(total),"page",page,"size",size);
    }
    public Map<String,Object> tournaments(Authentication auth,Map<String,String> filters,int page,int size){
        accounts.requireAdmin(auth);owned.flush();var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("name","owner"));
        AdminDirectoryService.choice(clauses,args,filters,"status",Set.of("DRAFT","REGISTRATION","CHECK_IN","READY","RUNNING","PAUSED","FINISHED","CANCELLED","RECOVERY_REQUIRED"));
        AdminDirectoryService.choice(clauses,args,filters,"format",Set.of("ROUND_ROBIN","SINGLE_ELIMINATION"));AdminDirectoryService.dates(clauses,args,filters);
        return page("""
            select t.id,t.name,t.format,t.status,t.created_at,t.started_at,t.owner_user_id,u.username as owner,
            (select count(*) from tournament_participants p where p.tournament_id=t.id) as participants,
            (select count(*) from tournament_matches m where m.tournament_id=t.id) as total_matches,
            (select count(*) from tournament_matches m where m.tournament_id=t.id and m.status='FINISHED') as finished_matches,
            (select count(*) from tournament_matches m where m.tournament_id=t.id and m.status='RUNNING') as running_matches,
            (select count(*) from tournament_matches m where m.tournament_id=t.id and m.status='FAILED') as failed_matches
            from tournaments t join users u on u.id=t.owner_user_id
            """,clauses,args,page,size,"created_at desc,id");
    }
    public Map<String,Object> tournament(Authentication auth,String id){accounts.requireAdmin(auth);owned.flush();return tournaments.view(tournaments.find(id));}
    public Map<String,Object> tournamentAction(Authentication auth,String id,String action,Long revision,String reason,String registration){
        accounts.requireAdmin(auth);reason=AdminDirectoryService.reason(reason);
        if(Set.of("pause","resume","next-round","finish").contains(action))administration.control(auth,id,revision,action,reason);
        else owned.adminLifecycle(auth,id,revision,action,reason,registration);
        return tournament(auth,id);
    }
}
