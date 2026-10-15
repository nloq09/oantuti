package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.replay.ReplayArchiveStore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class AdminReplayService {
    private final AdminCatalogService catalog;private final AdminDirectoryService query;private final AccountAuthorizationService accounts;private final ReplayArchiveStore archives;
    public AdminReplayService(AdminCatalogService catalog,AdminDirectoryService query,AccountAuthorizationService accounts,ReplayArchiveStore archives){this.catalog=catalog;this.query=query;this.accounts=accounts;this.archives=archives;}
    private static final String SOURCE="""
        select m.id,m.created_at,m.finished_at,tm.tournament_id,r.event_count,r.sha256,r.valid,
        case when r.id is null then 'MISSING' when r.valid=true then 'VALID' else 'INVALID' end as state,
        u1.display_name as player1,u2.display_name as player2
        from matches m left join replays r on r.match_id=m.id left join tournament_matches tm on tm.match_id=m.id
        left join users u1 on u1.id=m.player1_user_id left join users u2 on u2.id=m.player2_user_id where m.status='FINISHED'
        """;
    public Map<String,Object> list(Authentication auth,Map<String,String> filters,int page,int size){
        accounts.requireAdmin(auth);var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.choice(clauses,args,filters,"state",Set.of("VALID","INVALID","MISSING"));AdminDirectoryService.search(clauses,args,filters.get("q"),List.of("id","player1","player2"));AdminDirectoryService.dates(clauses,args,filters);
        if(AdminDirectoryService.nonempty(filters.get("tournament"))){clauses.add("tournament_id=?");args.add(AdminDirectoryService.uuid(filters.get("tournament")));}
        return catalog.page(SOURCE,clauses,args,page,size,"created_at desc,id");
    }
    public Map<String,Object> detail(Authentication auth,String id){accounts.requireAdmin(auth);var row=query.one("select * from ("+SOURCE+") inventory where id=?",id,"REPLAY_NOT_FOUND");row.put("storage_state",archives.availability(id));return row;}
    public Map<String,Object> validate(Authentication auth,String id,String explanation){
        accounts.requireAdmin(auth);AdminDirectoryService.reason(explanation);detail(auth,id);String state="VALID",code=null;
        try{archives.load(id);}catch(AuthException failure){if(failure.status()==503)throw failure;code=failure.getMessage();state=Set.of("REPLAY_FILE_MISSING","REPLAY_NOT_READY").contains(code)?"MISSING":"INVALID";}
        var row=detail(auth,id);row.put("validation_state",state);row.put("validation_code",code);row.put("checked_at",Instant.now());return row;
    }
}
