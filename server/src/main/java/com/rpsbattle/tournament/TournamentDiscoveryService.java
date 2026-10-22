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
    @org.springframework.beans.factory.annotation.Autowired java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.registry.TournamentRegistry registry;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.timeline.TimelineRegistrationStore applications;
    public Map<String,Object> discover(Authentication auth,String category,String search,String format,int page,int size){
        if(page<0||page>10000||size<1||size>50)throw new AuthException("INVALID_PAGE",400);
        String condition=switch(category){case "live"->"t.status in ('RUNNING','PAUSED','RECOVERY_REQUIRED')";case "open"->"t.status='REGISTRATION'";case "upcoming"->"t.status in ('DRAFT','CHECK_IN','READY')";case "finished"->"t.status in ('FINISHED','CANCELLED')";case "joined"->"1=1";default->throw new AuthException("INVALID_FILTER",400);};
        var user=accounts.current(auth);if(category.equals("joined")&&user.isEmpty())throw new AuthException("UNAUTHENTICATED",401);owned.advanceTimelineRegistrations();owned.flush();
        var args=new ArrayList<Object>();
        if(category.equals("open")||category.equals("upcoming")){
            var ids=new ArrayList<UUID>();var now=clock.instant();
            for(var cup:registry.findAll())synchronized(cup){if(category.equals(discoveryCategory(cup,now)))ids.add(UUID.fromString(cup.id));}
            condition=ids.isEmpty()?"1=0":"t.id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";args.addAll(ids);
        }
        String where=" from tournaments t join users u on u.id=t.owner_user_id where t.visibility='PUBLIC' and t.trashed_at is null and "+condition;
        if(category.equals("joined")){where+=" and (exists(select 1 from tournament_registrations r where r.tournament_id=t.id and r.user_id=?) or exists(select 1 from tournament_timeline_registrations r where r.tournament_id=t.id and r.user_id=? and r.status<>'CANCELLED'))";args.add(user.orElseThrow().id().value());args.add(user.orElseThrow().id().value());}
        if(search!=null&&!search.isBlank()){if(search.length()>80)throw new AuthException("INVALID_FILTER",400);where+=" and lower(t.name) like ? escape '!'";args.add("%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(format!=null&&!format.isBlank()){if(!Set.of("ROUND_ROBIN","SINGLE_ELIMINATION","GROUP_KNOCKOUT","SWISS","TEAM_ROUND_ROBIN").contains(format))throw new AuthException("INVALID_FILTER",400);where+=" and t.format=?";args.add(format);}
        long total=Objects.requireNonNull(db.queryForObject("select count(*)"+where,Long.class,args.toArray()));
        var select=new ArrayList<Object>();select.add(user.map(value->value.id().value()).orElse(null));select.add(user.map(value->value.id().value()).orElse(null));select.addAll(args);select.add(size);select.add((long)page*size);
        var items=db.query("select t.id,t.name,t.format,t.status,t.max_participants,u.username as organizer,t.visibility,t.created_at,t.started_at,(select count(*) from tournament_participants p where p.tournament_id=t.id) as participant_count,(exists(select 1 from tournament_registrations r where r.tournament_id=t.id and r.user_id=? and r.status in ('PENDING','APPROVED','CHECKED_IN')) or exists(select 1 from tournament_timeline_registrations r where r.tournament_id=t.id and r.user_id=? and r.status in ('PENDING','APPROVED','WAITLISTED'))) as registered"+where+" order by t.created_at desc,t.id limit ? offset ?",(rs,n)->{
            var row=new LinkedHashMap<String,Object>();for(String key:List.of("id","name","format","status","max_participants","organizer","visibility","participant_count","registered"))row.put(key,rs.getObject(key));
            var cup=tournaments.find(rs.getString("id"));synchronized(cup){var timing=cup.timing;var timeline=cup.timeline;row.put("startTime",timeline!=null?timeline.tournamentStartAt():timing.startTime()!=null?timing.startTime():rs.getTimestamp("started_at")==null?null:rs.getTimestamp("started_at").toInstant());row.put("registrationDeadline",timeline!=null?timeline.registrationCloseAt():timing.registrationDeadline());if(timeline!=null){row.put("phase",applications.phase(cup,clock.instant()).phase());var mine=user.isPresent()?applications.mine(UUID.fromString(cup.id),user.get().id().value()):null;row.put("applicationStatus",mine==null?null:mine.status());}}return row;
        },select.toArray());
        return Map.of("items",items,"page",page,"size",size,"total",total,"serverTime",clock.instant());
    }
    static String discoveryCategory(com.rpsbattle.tournament.domain.Tournament t,java.time.Instant now){
        if(Set.of(com.rpsbattle.tournament.domain.TournamentStatus.RUNNING,com.rpsbattle.tournament.domain.TournamentStatus.PAUSED,com.rpsbattle.tournament.domain.TournamentStatus.RECOVERY_REQUIRED).contains(t.status))return "live";
        if(Set.of(com.rpsbattle.tournament.domain.TournamentStatus.FINISHED,com.rpsbattle.tournament.domain.TournamentStatus.CANCELLED).contains(t.status))return "finished";
        if(t.status==com.rpsbattle.tournament.domain.TournamentStatus.DRAFT)return "upcoming";
        if(t.timeline!=null)return t.status==com.rpsbattle.tournament.domain.TournamentStatus.REGISTRATION&&!now.isBefore(t.timeline.registrationOpenAt())&&now.isBefore(t.timeline.registrationCloseAt())?"open":"upcoming";
        return t.status==com.rpsbattle.tournament.domain.TournamentStatus.REGISTRATION&&(t.timing.registrationDeadline()==null||now.isBefore(t.timing.registrationDeadline()))?"open":"upcoming";
    }
}
