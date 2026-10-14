package com.rpsbattle.admin;

import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.ownership.*;
import com.rpsbattle.bot.upload.BotUploadService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class AdminDirectoryService {
    private final JdbcTemplate db;
    private final AccountAuthorizationService authorization;
    private final AdminService accounts;
    private final BotRepository bots;
    private final BotUploadService artifacts;
    public AdminDirectoryService(JdbcTemplate db,AccountAuthorizationService authorization,AdminService accounts,BotRepository bots,BotUploadService artifacts){this.db=db;this.authorization=authorization;this.accounts=accounts;this.bots=bots;this.artifacts=artifacts;}
    private static final String USERS="select u.id,u.username,u.email,u.display_name,u.role,u.status,u.created_at,u.updated_at,u.last_login_at,(select count(*) from bots b where b.owner_user_id=u.id and b.status<>'DELETED') as bots,(select count(*) from matches m where m.player1_user_id=u.id or m.player2_user_id=u.id) as matches from users u";
    private static final String BOTS="""
        select b.id,b.owner_user_id,b.name,b.language,b.status,b.admin_disabled,b.created_at,b.updated_at,u.username as owner,
        (select max(m.started_at) from matches m join match_participants p on p.match_id=m.id where p.seat_bot_id=b.id) as last_used,
        (select count(*) from tournament_matches tm join tournament_participants tp on tp.tournament_id=tm.tournament_id and (tp.id=tm.participant_a_id or tp.id=tm.participant_b_id) where tp.bot_id=b.id and tm.failure is not null) as failures
        from bots b join users u on u.id=b.owner_user_id
        """;
    public Map<String,Object> list(Authentication auth,String kind,Map<String,String> filters,int page,int size){
        authorization.requireAdmin(auth);if(page<0||page>100000||size<1||size>100)throw new AuthException("INVALID_PAGE",400);
        var args=new ArrayList<Object>();var conditions=new ArrayList<String>();String source;
        if(kind.equals("users")){
            source=USERS;search(conditions,args,filters.get("q"),List.of("username","email","display_name"));
            choice(conditions,args,filters,"role",Set.of("USER","ADMIN"));choice(conditions,args,filters,"status",Set.of("ACTIVE","DISABLED"));
        }else if(kind.equals("bots")){
            source=BOTS;search(conditions,args,filters.get("q"),List.of("name","owner"));choice(conditions,args,filters,"language",Set.of("PYTHON","CPP","JAVA","JAR"));
            choice(conditions,args,filters,"status",Set.of("UPLOADED","READY","BUILD_ERROR","DELETED"));
            if(nonempty(filters.get("owner"))){conditions.add("owner_user_id=?");args.add(uuid(filters.get("owner")));}
            if("true".equals(filters.get("errors")))conditions.add("(failures>0 or status='BUILD_ERROR')");
            if("true".equals(filters.get("disabled")))conditions.add("admin_disabled=true");
        }else throw new AuthException("INVALID_RESOURCE",400);
        dates(conditions,args,filters);
        String query=" from ("+source+") directory"+(conditions.isEmpty()?"":" where "+String.join(" and ",conditions));
        long total=Objects.requireNonNull(db.queryForObject("select count(*)"+query,Long.class,args.toArray()));
        args.add(size);args.add((long)page*size);
        return Map.of("items",rows("select *"+query+" order by created_at desc,id limit ? offset ?",args.toArray()),"total",total,"page",page,"size",size);
    }
    public Map<String,Object> user(Authentication auth,UUID id){
        authorization.requireAdmin(auth);var result=one(USERS+" where u.id=?",id,"USER_NOT_FOUND");
        result.put("recent_matches",rows("select id,mode,status,outcome,turn_count,created_at from matches where player1_user_id=? or player2_user_id=? order by created_at desc limit 20",id,id));
        result.put("owned_bots",rows(BOTS+" where b.owner_user_id=? order by b.created_at desc limit 20",id));
        result.put("tournaments",rows("select distinct t.id,t.name,t.status,t.created_at from tournaments t left join tournament_participants p on p.tournament_id=t.id where t.owner_user_id=? or p.owner_user_id=? order by t.created_at desc limit 20",id,id));
        result.put("audit",audit("USER",id));return result;
    }
    public Map<String,Object> bot(Authentication auth,UUID id){
        authorization.requireAdmin(auth);var result=one(BOTS+" where b.id=?",id,"BOT_NOT_FOUND");
        try{result.put("artifact_hash",artifacts.sourceHash(id.toString()));result.put("storage_status","AVAILABLE");}catch(RuntimeException unavailable){result.put("artifact_hash",null);result.put("storage_status","UNAVAILABLE");}
        result.put("version_policy","Immutable SHA-256 artifact; frozen tournament versions remain unchanged.");
        result.put("recent_matches",rows("select m.id,m.mode,m.status,m.outcome,m.created_at from matches m join match_participants p on p.match_id=m.id where p.seat_bot_id=? order by m.created_at desc limit 20",id));
        result.put("recent_failures",rows("select tm.id,tm.tournament_id,tm.failure,tm.status,tm.finished_at from tournament_matches tm join tournament_participants tp on tp.tournament_id=tm.tournament_id and (tp.id=tm.participant_a_id or tp.id=tm.participant_b_id) where tp.bot_id=? and tm.failure is not null order by tm.finished_at desc limit 20",id));
        result.put("audit",audit("BOT",id));return result;
    }
    @Transactional public User userStatus(Authentication auth,UUID id,AccountStatus status,String reason){
        var actor=authorization.requireAdmin(auth);reason=reason(reason);
        var before=one("select status from users where id=? for update",id,"USER_NOT_FOUND").get("status");
        var user=accounts.setStatus(auth,new UserId(id),status);
        if(!before.equals(status.name()))record(actor,"USER",id,status==AccountStatus.ACTIVE?"ENABLE":"DISABLE",reason,before.toString(),status.name());return user;
    }
    @Transactional public Map<String,Object> botStatus(Authentication auth,UUID id,String action,String reason){
        var actor=authorization.requireAdmin(auth);reason=reason(reason);var bot=bots.lock(id).orElseThrow(()->new AuthException("BOT_NOT_FOUND",404));
        if(bot.status()==BotMetadata.Status.DELETED)throw new AuthException("BOT_ARCHIVED",409);
        if(!Set.of("enable","disable","archive").contains(action))throw new AuthException("INVALID_ACTION",400);
        var row=one("select admin_disabled from bots where id=?",id,"BOT_NOT_FOUND");String before=Boolean.TRUE.equals(row.get("admin_disabled"))?"DISABLED":"ENABLED";
        if(action.equals("archive")){bots.save(bot.deleted());record(actor,"BOT",id,"ARCHIVE",reason,before,"DELETED");}
        else {boolean disabled=action.equals("disable");if(Boolean.TRUE.equals(row.get("admin_disabled"))!=disabled){db.update("update bots set admin_disabled=?,updated_at=? where id=?",disabled,Instant.now(),id);record(actor,"BOT",id,action.toUpperCase(Locale.ROOT),reason,before,disabled?"DISABLED":"ENABLED");}}
        return bot(auth,id);
    }
    private void record(User actor,String kind,UUID id,String action,String reason,String before,String after){db.update("insert into admin_audit_events(id,actor_user_id,target_type,target_id,action,reason,before_value,after_value,occurred_at) values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),actor.id().value(),kind,id,action,reason,before,after,Instant.now());}
    private List<Map<String,Object>> audit(String kind,UUID id){return rows("select a.action,a.reason,a.before_value,a.after_value,a.occurred_at,u.username as actor from admin_audit_events a join users u on u.id=a.actor_user_id where a.target_type=? and a.target_id=? order by a.occurred_at desc limit 50",kind,id);}
    public static String reason(String value){if(value==null||value.strip().isEmpty()||value.strip().length()>500||value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new AuthException("REASON_REQUIRED",400);return value.strip();}
    static UUID uuid(String value){try{return UUID.fromString(value);}catch(RuntimeException invalid){throw new AuthException("INVALID_FILTER",400);}}
    static boolean nonempty(String value){return value!=null&&!value.isBlank();}
    static void search(List<String> conditions,List<Object> args,String value,List<String> columns){if(!nonempty(value))return;if(value.length()>200)throw new AuthException("INVALID_FILTER",400);conditions.add("("+String.join(" or ",columns.stream().map(c->"lower("+c+") like ? escape '!' ").toList())+")");String query="%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";columns.forEach(c->args.add(query));}
    static void choice(List<String> conditions,List<Object> args,Map<String,String> filters,String key,Set<String> values){String value=filters.get(key);if(!nonempty(value))return;if(!values.contains(value))throw new AuthException("INVALID_FILTER",400);conditions.add(key+"=?");args.add(value);}
    static void dates(List<String> conditions,List<Object> args,Map<String,String> filters){try{if(nonempty(filters.get("from"))){conditions.add("created_at>=?");args.add(LocalDate.parse(filters.get("from")).atStartOfDay().toInstant(ZoneOffset.UTC));}if(nonempty(filters.get("to"))){conditions.add("created_at<?");args.add(LocalDate.parse(filters.get("to")).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));}}catch(DateTimeException invalid){throw new AuthException("INVALID_FILTER",400);}}
    Map<String,Object> one(String sql,Object id,String error){var rows=rows(sql,id);if(rows.isEmpty())throw new AuthException(error,404);return rows.getFirst();}
    List<Map<String,Object>> rows(String sql,Object...args){return db.query(sql,(rs,n)->{var row=new LinkedHashMap<String,Object>();var metadata=rs.getMetaData();for(int i=1;i<=metadata.getColumnCount();i++){var value=rs.getObject(i);if(value instanceof java.sql.Timestamp stamp)value=stamp.toInstant();if(value instanceof java.time.OffsetDateTime stamp)value=stamp.toInstant();row.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT),value);}return row;},args);}
}
