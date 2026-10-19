package com.rpsbattle.tournament;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.tournament.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties="rps.coding.worker-auto-dispatch=false")
class LargeTournamentDeadlineTest extends TimelineCodingApiTest {
 @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 @Autowired CodeWorkspaceService workspaces;
 @Autowired CodingQueueStore queue;
 @Autowired CodingDeadlineService deadlines;
 @Autowired CodingWorker worker;
 @Override com.fasterxml.jackson.databind.JsonNode done(String base)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<until){worker.dispatchJobs();var w=request(base+"/workspace/me?language=PYTHON","GET",null,200);if(!Set.of("BUILDING","TESTING").contains(w.path("status").asText()))return w;Thread.sleep(25);}throw new AssertionError("Coding job timeout");}
 @Test void twoThousandRegistrationsThousandWorkspacesAndDeadlineBurstRemainBounded()throws Exception{
  loginAccount();var cup=request("/api/tournaments","POST",Map.of("name","Scale 2000","format","SINGLE_ELIMINATION","maxParticipants",2000,"botPolicy",Map.of("submissionMode","CODE_IN_PLATFORM_ONLY")),201);String id=cup.path("id").asText(),base="/api/tournaments/"+id;var tl=new TournamentTimeline(START,START.plusSeconds(60),START.plusSeconds(120),START.plusSeconds(180),START.plusSeconds(190),START.plusSeconds(600),START.plusSeconds(600),START.plusSeconds(660),START.plusSeconds(720),START.plusSeconds(780),null);request(base+"/timeline","PUT",Map.of("revision",cup.path("revision").asLong(),"timeline",tl),200);request(base+"/open-registration","POST",Map.of(),200);
  var users=new ArrayList<User>();var userRows=new ArrayList<Object[]>();var registrations=new ArrayList<Object[]>();UUID tid=UUID.fromString(id);for(int i=0;i<2000;i++){UUID uid=UUID.randomUUID();String name="load_"+uid.toString().substring(0,20);users.add(new User(new UserId(uid),name,name+"@example.invalid","fixture","Load player",UserRole.USER,AccountStatus.ACTIVE,START,START,null));userRows.add(new Object[]{uid,name,name+"@example.invalid","fixture","Load player"});registrations.add(new Object[]{UUID.randomUUID(),tid,uid,java.sql.Timestamp.from(START),java.sql.Timestamp.from(START),account.id().value()});}
  db.batchUpdate("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",userRows);db.batchUpdate("insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,status,registered_at,rules_accepted_at,review_reason,reviewed_at,reviewed_by) values(?,?,?,'Load player','PYTHON','APPROVED',?,?,'Scale fixture',CURRENT_TIMESTAMP,?)",registrations);
  request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","PYTHON"),409);when(clock.instant()).thenReturn(START.plusSeconds(120));long started=System.nanoTime();for(int i=0;i<1000;i++)workspaces.create(AuthService.sessionAuthentication(users.get(i)),id,"PYTHON");
  try(var pool=Executors.newFixedThreadPool(12)){var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<1000;i++){var user=users.get(i);tasks.add(()->{workspaces.save(AuthService.sessionAuthentication(user),id,"PYTHON",0L,"print('load saved')\n");return null;});}for(var result:pool.invokeAll(tasks))result.get(120,TimeUnit.SECONDS);}
  assertThat(db.queryForObject("select count(*) from tournament_code_workspaces where tournament_id=? and revision=1",Integer.class,tid)).isEqualTo(1000);var auth=AuthService.sessionAuthentication(users.getFirst());assertThatThrownBy(()->workspaces.save(auth,id,"PYTHON",0L,"stale overwrite")).hasMessage("WORKSPACE_CONFLICT");
  try{for(int i=0;i<200;i++)workspaces.job(AuthService.sessionAuthentication(users.get(i)),id,"PYTHON",1L,"print('queued')\n","BUILD");assertThatThrownBy(()->workspaces.job(AuthService.sessionAuthentication(users.get(200)),id,"PYTHON",1L,"print('overload')\n","BUILD")).hasMessage("WORKSPACE_QUEUE_FULL");assertThat(queue.lease()).isNotNull();assertThat(queue.lease()).isNotNull();assertThat(queue.lease()).isNull();assertThat(db.queryForObject("select count(*) from bot_coding_jobs where status='RUNNING'",Integer.class)).isEqualTo(2);}finally{db.update("update bot_coding_jobs set status='FAILED',finished_at=CURRENT_TIMESTAMP where workspace_id in (select id from tournament_code_workspaces where tournament_id=?)",tid);}
  when(clock.instant()).thenReturn(START.plusSeconds(180));assertThatThrownBy(()->workspaces.save(auth,id,"PYTHON",2L,"late reconnect")).hasMessage("CODING_NOT_OPEN");int previous=0;for(int i=0;i<120&&previous<2000;i++){deadlines.processDeadlines();int count=db.queryForObject("select count(*) from tournament_coding_freezes where tournament_id=?",Integer.class,tid);assertThat(count-previous).isBetween(0,25);previous=count;}assertThat(previous).isEqualTo(2000);assertThat(db.queryForObject("select count(*) from tournament_coding_freezes where tournament_id=? and decision='MISSING'",Integer.class,tid)).isEqualTo(2000);long revision=tournaments.find(id).revision;deadlines.processDeadlines();assertThat(tournaments.find(id).revision).isEqualTo(revision);verify(sandbox,never()).build(any(),any());System.out.println("Scale fixture PASS: 2000 registrations, 1000 workspaces/autosaves, queue <=200/running <=2, 2000 freezes; elapsedMs="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
 }
}
