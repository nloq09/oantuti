package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.MatchRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AdminMatchReadService {
    private final JdbcTemplate db;private final AdminDirectoryService query;private final AccountAuthorizationService authorization;private final MatchRegistry rooms;
    public AdminMatchReadService(JdbcTemplate db,AdminDirectoryService query,AccountAuthorizationService authorization,MatchRegistry rooms){this.db=db;this.query=query;this.authorization=authorization;this.rooms=rooms;}
    private static final String SOURCE="""
        select m.id,m.room_id,m.mode,m.status,m.turn_count,m.started_at,m.finished_at,m.duration_ms,m.created_at,m.outcome,m.winner_seat,m.rule_set,
        m.player1_user_id,m.player2_user_id,tm.tournament_id,tm.id as tournament_match_id,tm.failure,
        p1.seat_bot_id as bot1_id,p2.seat_bot_id as bot2_id,
        coalesce(u1.display_name,b1.name,p1.controller_type,'Guest') as player1,
        coalesce(u2.display_name,b2.name,p2.controller_type,'Guest') as player2,
        exists(select 1 from replays r where r.match_id=m.id and r.valid=true) as replay_available
        from matches m left join tournament_matches tm on tm.match_id=m.id
        left join match_participants p1 on p1.match_id=m.id and p1.seat='P1'
        left join match_participants p2 on p2.match_id=m.id and p2.seat='P2'
        left join users u1 on u1.id=m.player1_user_id left join users u2 on u2.id=m.player2_user_id
        left join bots b1 on b1.id=p1.seat_bot_id left join bots b2 on b2.id=p2.seat_bot_id
        union all
        select cast(tm.id as varchar(64)),cast(null as varchar(64)),'TOURNAMENT',tm.status,0,tm.started_at,tm.finished_at,cast(null as bigint),t.created_at,tm.outcome,cast(null as varchar(2)),cast(null as varchar(80)),
        a.owner_user_id,b.owner_user_id,tm.tournament_id,tm.id,tm.failure,a.bot_id,b.bot_id,a.display_name,b.display_name,false
        from tournament_matches tm join tournaments t on t.id=tm.tournament_id
        join tournament_participants a on a.id=tm.participant_a_id join tournament_participants b on b.id=tm.participant_b_id where tm.match_id is null
        """;
    public Map<String,Object> list(Authentication auth,Map<String,String> filters,int page,int size){
        authorization.requireAdmin(auth);if(page<0||page>100000||size<1||size>100)throw new AuthException("INVALID_PAGE",400);
        var conditions=new ArrayList<String>();var args=new ArrayList<Object>();
        AdminDirectoryService.search(conditions,args,filters.get("q"),List.of("id","player1","player2"));
        String status=filters.get("status");
        if("RUNNING".equals(status))conditions.add("status in ('RUNNING','PLAYING')");
        else if("ERROR".equals(status))conditions.add("(status in ('FAILED','INFRASTRUCTURE_ERROR') or failure is not null)");
        else AdminDirectoryService.choice(conditions,args,filters,"status",Set.of("WAITING","PLACEMENT","PLAYING","PENDING","FINISHED","FAILED","INFRASTRUCTURE_ERROR"));
        for(String key:List.of("tournament","user","bot"))if(AdminDirectoryService.nonempty(filters.get(key))){var id=AdminDirectoryService.uuid(filters.get(key));if(key.equals("tournament")){conditions.add("tournament_id=?");args.add(id);}else{String a=key.equals("user")?"player1_user_id":"bot1_id",b=key.equals("user")?"player2_user_id":"bot2_id";conditions.add("("+a+"=? or "+b+"=?)");args.add(id);args.add(id);}}
        AdminDirectoryService.dates(conditions,args,filters);String source=" from ("+SOURCE+") directory"+(conditions.isEmpty()?"":" where "+String.join(" and ",conditions));
        Long total=db.queryForObject("select count(*)"+source,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        var items=query.rows("select *"+source+" order by created_at desc,id limit ? offset ?",args.toArray());items.forEach(this::live);
        return Map.of("items",items,"total",Objects.requireNonNull(total),"page",page,"size",size);
    }
    public Map<String,Object> detail(Authentication auth,String id){
        authorization.requireAdmin(auth);if(id.length()>64)throw new AuthException("MATCH_NOT_FOUND",404);
        var row=query.one("select * from ("+SOURCE+") directory where id=?",id,"MATCH_NOT_FOUND");live(row);
        row.put("participants",query.rows("select p.seat,p.seat_user_id,p.seat_bot_id,p.controller_type,u.username,b.name as bot_name from match_participants p left join users u on u.id=p.seat_user_id left join bots b on b.id=p.seat_bot_id where p.match_id=? order by p.seat",id));
        row.put("timeline",query.rows("select event_type,reason,before_value,after_value,occurred_at from tournament_audit_events where target_id=? order by occurred_at desc limit 50",Objects.toString(row.get("tournament_match_id"),id)));
        return row;
    }
    private void live(Map<String,Object> row){
        row.put("live_available",false);
        for(var room:rooms.getActiveMatches())if(room.archiveId().equals(row.get("id"))){synchronized(room){
            row.put("status",room.infrastructureError()!=null?"INFRASTRUCTURE_ERROR":room.status());row.put("turn_count",room.state().turnNumber());
            row.put("current_player",room.state().currentPlayerId().value());row.put("sequence",room.state().sequence());
            row.put("bot_error",room.botError());row.put("infrastructure_error",room.infrastructureError());
            row.put("live_available",!room.state().isFinished()&&room.infrastructureError()==null);
            row.put("move_count_p1",room.botMoveCount(com.rpsbattle.online.OnlineMatch.P1));row.put("move_count_p2",room.botMoveCount(com.rpsbattle.online.OnlineMatch.P2));
            row.put("move_nanos_p1",room.botMoveNanos(com.rpsbattle.online.OnlineMatch.P1));row.put("move_nanos_p2",room.botMoveNanos(com.rpsbattle.online.OnlineMatch.P2));
        }break;}
    }
}
