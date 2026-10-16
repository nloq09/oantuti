package com.rpsbattle.tournament;
import com.rpsbattle.auth.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
/** Bounded public metadata projection; never loads archives or full schedules. */
@Service
public class TournamentDiscoveryService {
    private final JdbcTemplate db;private final AuthService accounts;private final OwnedTournamentService owned;private final TournamentService tournaments;
    public TournamentDiscoveryService(JdbcTemplate db,AuthService accounts,OwnedTournamentService owned,TournamentService tournaments){this.db=db;this.accounts=accounts;this.owned=owned;this.tournaments=tournaments;}
    public Map<String,Object> discover(Authentication auth,String category,String search,String format,int page,int size){
        if(page<0||page>10000||size<1||size>50)throw new AuthException("INVALID_PAGE",400);
        String condition=switch(category){case "live"->"t.status in ('RUNNING','PAUSED','RECOVERY_REQUIRED')";case "open"->"t.status='REGISTRATION'";case "upcoming"->"t.status in ('DRAFT','CHECK_IN','READY')";case "finished"->"t.status in ('FINISHED','CANCELLED')";case "joined"->"1=1";default->throw new AuthException("INVALID_FILTER",400);};
        var user=accounts.current(auth);if(category.equals("joined")&&user.isEmpty())throw new AuthException("UNAUTHENTICATED",401);owned.flush();
        var args=new ArrayList<Object>();String where=" from tournaments t join users u on u.id=t.owner_user_id where t.visibility='PUBLIC' and "+condition;
        if(category.equals("joined")){where+=" and exists(select 1 from tournament_registrations r where r.tournament_id=t.id and r.user_id=?)";args.add(user.orElseThrow().id().value());}
        if(search!=null&&!search.isBlank()){if(search.length()>80)throw new AuthException("INVALID_FILTER",400);where+=" and lower(t.name) like ? escape '!'";args.add("%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(format!=null&&!format.isBlank()){if(!Set.of("ROUND_ROBIN","SINGLE_ELIMINATION","GROUP_KNOCKOUT").contains(format))throw new AuthException("INVALID_FILTER",400);where+=" and t.format=?";args.add(format);}
        long total=Objects.requireNonNull(db.queryForObject("select count(*)"+where,Long.class,args.toArray()));
        var select=new ArrayList<Object>();select.add(user.map(value->value.id().value()).orElse(null));select.addAll(args);select.add(size);select.add((long)page*size);
        var items=db.query("select t.id,t.name,t.format,t.status,t.max_participants,u.username as organizer,t.visibility,t.created_at,t.started_at,(select count(*) from tournament_participants p where p.tournament_id=t.id) as participant_count,exists(select 1 from tournament_registrations r where r.tournament_id=t.id and r.user_id=? and r.status in ('PENDING','APPROVED','CHECKED_IN')) as registered"+where+" order by t.created_at desc,t.id limit ? offset ?",(rs,n)->{
            var row=new LinkedHashMap<String,Object>();for(String key:List.of("id","name","format","status","max_participants","organizer","visibility","participant_count","registered"))row.put(key,rs.getObject(key));
            var timing=tournaments.find(rs.getString("id")).timing;row.put("startTime",timing.startTime()!=null?timing.startTime():rs.getTimestamp("started_at")==null?null:rs.getTimestamp("started_at").toInstant());row.put("registrationDeadline",timing.registrationDeadline());return row;
        },select.toArray());
        return Map.of("items",items,"page",page,"size",size,"total",total,"serverTime",java.time.Instant.now());
    }
}
