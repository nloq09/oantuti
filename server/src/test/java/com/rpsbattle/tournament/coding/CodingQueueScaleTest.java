package com.rpsbattle.tournament.coding;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Timeout(180)
class CodingQueueScaleTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired WorkspaceStore workspaces;
 @Autowired CodingQueueStore queue;
 @MockitoBean CodingWorker worker;
 UUID cup;List<UUID> users=new ArrayList<>();
 @BeforeEach void seed(){cup=UUID.randomUUID();String prefix="load_"+cup.toString().substring(0,8);for(int i=0;i<1000;i++)users.add(UUID.randomUUID());
  jdbc.batchUpdate("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",users,100,(ps,u)->{String name=prefix+"_"+users.indexOf(u);ps.setObject(1,u);ps.setString(2,name);ps.setString(3,name+"@example.com");ps.setString(4,"unused test fixture");ps.setString(5,name);});
  jdbc.update("insert into tournaments(id,owner_user_id,name,format,status) values(?,?,?,'ROUND_ROBIN','REGISTRATION')",cup,users.getFirst(),"Bounded queue fixture");
  jdbc.update("insert into tournament_bot_policies(tournament_id,submission_mode,allowed_languages,policy_json) values(?,'PRE_UPLOADED_ONLY','CPP,JAVA,PYTHON','{}')",cup);
  jdbc.batchUpdate("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,revision,status,created_at,updated_at) values(?,?,?,'PYTHON','pass',0,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",users,100,(ps,u)->{ps.setObject(1,UUID.randomUUID());ps.setObject(2,cup);ps.setObject(3,u);});
 }
 @AfterEach void settle(){jdbc.update("update bot_coding_jobs set status='FAILED',finished_at=CURRENT_TIMESTAMP where workspace_id in (select id from tournament_code_workspaces where tournament_id=?)",cup);}
 @Test void thousandWorkspacesAndFiveHundredConcurrentSavesThenBoundedBuildAndTestBursts()throws Exception{
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var tasks=new ArrayList<Future<?>>();for(int i=0;i<500;i++){UUID user=users.get(i);tasks.add(executor.submit(()->workspaces.save(cup,user,"PYTHON",0,"print('saved')")));}for(var task:tasks)task.get(120,TimeUnit.SECONDS);}
  assertThat(jdbc.queryForObject("select count(*) from tournament_code_workspaces where tournament_id=?",Integer.class,cup)).isEqualTo(1000);assertThat(jdbc.queryForObject("select count(*) from tournament_code_workspaces where tournament_id=? and revision=1",Integer.class,cup)).isEqualTo(500);
  for(int i=0;i<200;i++)workspaces.claim(cup,users.get(i),"PYTHON",1,"print('build')",10,"BUILD");
  assertThatThrownBy(()->workspaces.claim(cup,users.get(201),"PYTHON",1,"pass",10,"BUILD")).hasMessage("WORKSPACE_QUEUE_FULL");assertThat(jdbc.queryForObject("select count(*) from bot_coding_jobs where status='QUEUED'",Integer.class)).isEqualTo(200);
  var first=queue.lease();var second=queue.lease();assertThat(first).isNotNull();assertThat(second).isNotNull();assertThat(queue.lease()).isNull();queue.complete(first,new WorkspaceSandbox.Build(true,UUID.randomUUID(),"a".repeat(64),"PASS"),null,null,null,null,null,null);queue.complete(second,new WorkspaceSandbox.Build(true,UUID.randomUUID(),"b".repeat(64),"PASS"),null,null,null,null,null,null);
  for(int i=2;i<200;i++){var lease=queue.lease();assertThat(lease).isNotNull();queue.complete(lease,new WorkspaceSandbox.Build(true,UUID.randomUUID(),"c".repeat(64),"PASS"),null,null,null,null,null,null);}
  for(int i=0;i<200;i++){var w=workspaces.read(cup,users.get(i),"PYTHON");workspaces.claim(cup,users.get(i),"PYTHON",w.revision(),null,10,"TEST");}
  assertThat(jdbc.queryForObject("select count(*) from bot_coding_jobs where status='QUEUED'",Integer.class)).isEqualTo(200);assertThatThrownBy(queue::admit).hasMessage("WORKSPACE_QUEUE_FULL");
 }
 @Test void expiredLeaseCanBeReclaimedAndOldWorkerCannotOverwriteNewCompletion(){
  workspaces.claim(cup,users.getFirst(),"PYTHON",0,"pass",10,"BUILD");var old=queue.lease();jdbc.update("update bot_coding_jobs set lease_until=? where id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)),old.id());var current=queue.lease();assertThat(current.id()).isEqualTo(old.id());assertThat(current.token()).isNotEqualTo(old.token());
  queue.complete(old,new WorkspaceSandbox.Build(false,null,null,"stale worker"),null,null,null,null,null,null);assertThat(workspaces.read(cup,users.getFirst(),"PYTHON").status()).isEqualTo("BUILDING");queue.complete(current,new WorkspaceSandbox.Build(true,UUID.randomUUID(),"d".repeat(64),"new worker"),null,null,null,null,null,null);assertThat(workspaces.read(cup,users.getFirst(),"PYTHON").status()).isEqualTo("BUILT");
 }
}
