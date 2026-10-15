package com.rpsbattle.admin;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.worker.*;
import com.rpsbattle.online.MatchRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
@Service
public class AdminOperationsService {
    private final AccountAuthorizationService accounts;private final JdbcTemplate db;private final DockerHealthProbe docker;private final WorkerManager workers;private final ExecutionQueue queue;private final MatchRegistry matches;private final MeterRegistry metrics;private final AdminOperationJournal journal;private final String botDirectory,replayDirectory;
    public AdminOperationsService(AccountAuthorizationService accounts,JdbcTemplate db,DockerHealthProbe docker,WorkerManager workers,ExecutionQueue queue,MatchRegistry matches,MeterRegistry metrics,AdminOperationJournal journal,@Value("${rps.bots.directory:data/bots}")String botDirectory,@Value("${rps.replays.directory:data/replays}")String replayDirectory){this.accounts=accounts;this.db=db;this.docker=docker;this.workers=workers;this.queue=queue;this.matches=matches;this.metrics=metrics;this.journal=journal;this.botDirectory=botDirectory;this.replayDirectory=replayDirectory;}
    public Map<String,Object> system(Authentication auth){
        accounts.requireAdmin(auth);var data=new LinkedHashMap<String,Object>();var alerts=new ArrayList<Map<String,Object>>();Instant now=Instant.now();
        data.put("checkedAt",now);data.put("application","UP");String database="UP";try{db.queryForObject("select 1",Integer.class);}catch(RuntimeException failure){database="DOWN";alerts.add(alert("DATABASE_UNAVAILABLE","CRITICAL","PostgreSQL chưa phản hồi."));}data.put("postgresql",database);
        var dockerState=docker.status();data.put("docker",dockerState);if(!dockerState.get("status").equals("UP"))alerts.add(alert("DOCKER_UNAVAILABLE","WARNING","Docker Linux chưa sẵn sàng; bot tải lên có thể không chạy được."));
        data.put("workers",workers.status());data.put("queue",queue.snapshot());data.put("queueCount",queue.size());data.put("queueCapacity",queue.capacity());
        if(workers.draining())alerts.add(alert("WORKER_DRAINING","WARNING","Worker đang dừng nhận trận mới."));if(queue.size()>=Math.max(1,queue.capacity()*8/10))alerts.add(alert("QUEUE_PRESSURE","WARNING","Queue đã dùng ít nhất 80% sức chứa."));
        data.put("activeMatches",matches.getActiveMatches().stream().filter(m->!m.state().isFinished()&&m.infrastructureError()==null).count());data.put("onlineUsers",gauge("active_users"));data.put("webSocketSessions",gauge("websocket_sessions"));
        data.put("jvmMemoryUsedBytes",metrics.find("jvm.memory.used").gauges().stream().mapToDouble(g->Math.max(0,g.value())).sum());
        var storage=Map.of("bots",storage(botDirectory),"replays",storage(replayDirectory));data.put("storage",storage);if(storage.values().stream().anyMatch(value->!"UP".equals(value.get("status"))))alerts.add(alert("STORAGE_DEGRADED","WARNING","Một thư mục bot/replay chưa sẵn sàng hoặc thiếu dung lượng."));
        if(database.equals("UP")){
            long recovery=count("select count(*) from tournaments where status='RECOVERY_REQUIRED'");if(recovery>0)alerts.add(alert("TOURNAMENT_RECOVERY","CRITICAL",recovery+" giải cần kiểm tra sau lỗi hoặc restart."));
            long invalid=count("select count(*) from replays where valid=false");if(invalid>0)alerts.add(alert("INVALID_REPLAY","WARNING",invalid+" replay đang được đánh dấu không hợp lệ."));
            var cutoff=java.sql.Timestamp.from(now.minusSeconds(900));long recent=count("select count(*) from tournament_matches where finished_at>=?",cutoff),failed=count("select count(*) from tournament_matches where failure='BOT_FAILED' and finished_at>=?",cutoff);
            if(recent>=5&&failed*2>=recent)alerts.add(alert("BOT_FAILURE_SPIKE","WARNING","Ít nhất 50% trận trong 15 phút gần đây có lỗi bot."));
            long longRunning=count("select count(*) from tournament_matches where status='RUNNING' and started_at<?",cutoff);if(longRunning>0)alerts.add(alert("LONG_RUNNING_MATCH","WARNING",longRunning+" trận đã chạy quá 15 phút; cần kiểm tra."));
            data.put("runningTournaments",count("select count(*) from tournaments where status='RUNNING'"));
            data.put("openDisputes",count("select count(*) from tournament_audit_events e where e.event_type='MATCH_REVIEW_REQUESTED' and not exists(select 1 from tournament_audit_events a where a.tournament_id=e.tournament_id and a.target_id=e.target_id and a.sequence_number>e.sequence_number and a.event_type in ('MATCH_REVIEW_REQUESTED','MATCH_REVIEW_CLOSED','MATCH_REVIEW_REJECTED','MATCH_RETRIED','RESULT_OVERRIDDEN'))"));
        }
        data.put("pendingAudit",journal.pending());if(journal.pending()>0)alerts.add(alert("AUDIT_WRITE_PENDING","CRITICAL","Audit HTTP đang chờ ghi lại vào DB."));data.put("alerts",alerts);data.put("status",database.equals("DOWN")?"DOWN":alerts.isEmpty()?"UP":"DEGRADED");return data;
    }
    private Object gauge(String name){var value=metrics.find(name).gauge();return value==null?null:value.value();}
    private long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    private static Map<String,Object> alert(String code,String severity,String message){return Map.of("code",code,"severity",severity,"message",message);}
    private static Map<String,Object> storage(String directory){var result=new LinkedHashMap<String,Object>();try{var path=Path.of(directory).toAbsolutePath().normalize();boolean ready=Files.isDirectory(path)&&Files.isReadable(path)&&Files.isWritable(path);var parent=path;while(parent!=null&&!Files.exists(parent))parent=parent.getParent();if(parent==null)throw new IllegalStateException();var store=Files.getFileStore(parent);result.put("usableBytes",store.getUsableSpace());result.put("status",ready&&store.getUsableSpace()>100L*1024*1024?"UP":"DEGRADED");}catch(Exception unavailable){result.put("status","DOWN");}return result;}
}
