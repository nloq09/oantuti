package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.coding.SubmissionStore;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Automatic shared pool; existing explicit local/remote policies remain authoritative. */
@Service
public class RemoteRoutingService {
    public record Policy(String mode,String minimumTrust,int maxMatches,long revision){}
    public record Configure(long revision,String mode,String minimumTrust,Integer maxMatches,String reason){}
    private final JdbcTemplate db;private final RemoteMatchQueue queue;private final SubmissionStore submissions;private final TournamentRegistry registry;private final Clock clock;
    public RemoteRoutingService(JdbcTemplate db,RemoteMatchQueue queue,SubmissionStore submissions,TournamentRegistry registry,Clock clock){this.db=db;this.queue=queue;this.submissions=submissions;this.registry=registry;this.clock=clock;}
    public Policy policy(String id){var rows=db.query("select * from worker_pool_policies where tournament_id=?",(r,n)->new Policy(r.getString("mode"),r.getString("minimum_trust"),r.getInt("max_matches"),r.getLong("revision")),UUID.fromString(id));return rows.isEmpty()?new Policy("AUTO","TRUSTED",registry.findById(id).map(t->t.maxConcurrentMatches).orElse(4),0):rows.getFirst();}
    private boolean supported(Tournament t){return t.participantPolicy==ParticipantPolicy.BOT_ONLY&&submissions.enabled(t.id);}
    public boolean remote(String id){var p=policy(id);return p.mode().equals("REMOTE_REQUIRED")||p.mode().equals("AUTO")&&registry.findById(id).filter(this::supported).isPresent();}
    public int concurrency(Tournament t){var p=policy(t.id);return p.mode().equals("LOCAL")?t.maxConcurrentMatches:p.maxMatches();}
    @Transactional public Policy configure(String id,UUID actor,Configure body){
        var t=registry.findById(id).orElseThrow(()->new AuthException("TOURNAMENT_NOT_FOUND",404));
        if(body==null||!Set.of("AUTO","LOCAL","REMOTE_REQUIRED").contains(Objects.toString(body.mode(),""))||body.reason()==null||body.reason().isBlank()||body.reason().length()>500||body.maxMatches()==null||body.maxMatches()<1||body.maxMatches()>256||!Set.of("TRUSTED","COMMUNITY").contains(Objects.toString(body.minimumTrust(),"")))throw new AuthException("INVALID_REMOTE_POLICY",400);
        synchronized(t){
            db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);var before=policy(id);
            if(before.revision()!=body.revision())throw new AuthException("STALE_REMOTE_POLICY",409);
            if(!Set.of(TournamentStatus.DRAFT,TournamentStatus.REGISTRATION).contains(t.status)||!t.matches.isEmpty()||db.queryForObject("select count(*) from match_execution_jobs where tournament_id=?",Integer.class,UUID.fromString(id))>0)throw new AuthException("REMOTE_POLICY_LOCKED",409);
            if(body.mode().equals("REMOTE_REQUIRED")){
                if(t.participantPolicy!=ParticipantPolicy.BOT_ONLY||!submissions.enabled(id))throw new AuthException("REMOTE_REQUIRES_LOCKED_CODING_BOTS",409);
            }
            db.update("delete from worker_pool_policies where tournament_id=?",UUID.fromString(id));db.update("insert into worker_pool_policies(tournament_id,mode,minimum_trust,max_matches,revision) values(?,?,?,?,?)",UUID.fromString(id),body.mode(),body.minimumTrust(),body.maxMatches(),before.revision()+1);
            db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at,reason,before_value,after_value) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),"TOURNAMENT_WORKER_POLICY",UUID.fromString(id),actor,java.sql.Timestamp.from(clock.instant()),body.reason().strip(),before.mode(),body.mode()+":"+body.minimumTrust()+":"+body.maxMatches());return policy(id);
        }
    }
    private RemoteMatchQueue.BotRef bot(TournamentMatch match,TournamentParticipant seat){
        var work=submissions.execution(match.tournamentId,seat.participantId(),seat.botId());
        // execution() validates the locked submission/version, official validation and bot identity.
        // The seat hash identifies the legacy upload snapshot, not this version's compiled artifact.
        if(work==null||work.artifactId()==null||work.artifactHash()==null)throw new AuthException("REMOTE_ARTIFACT_NOT_LOCKED",409);
        String language=work.language().equals("JAR")?"JAVA":work.language();return new RemoteMatchQueue.BotRef(work.artifactId(),work.artifactHash(),language,com.rpsbattle.bot.upload.BotLanguage.valueOf(work.language()).image);
    }
    @Transactional public boolean route(Tournament t,TournamentMatch match){
        var p=policy(t.id);if(p.mode().equals("LOCAL")||!supported(t))return false;
        if(match.participantA.botId()==null||match.participantB.botId()==null)throw new AuthException("REMOTE_REQUIRES_LOCKED_CODING_BOTS",409);
        // Same guard is reentrant in this transaction and fences the route row with admission.
        db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);
        var previous=db.queryForList("select id from match_execution_jobs where match_id=?",UUID.fromString(match.id));
        var a=bot(match,match.participantA);var b=bot(match,match.participantB);
        // Decide before admission. An admitted job never falls back to local execution.
        if(p.mode().equals("AUTO")&&previous.isEmpty()&&!queue.hasEligibleWorker(UUID.fromString(t.id),Set.copyOf(List.of(a.language(),b.language())),p.minimumTrust()))return false;
        if(!queue.hasCapacity())return true; // Backpressure: leave PENDING for a later scheduler tick.
        var spec=new RemoteMatchQueue.Spec(UUID.fromString(match.id),UUID.fromString(t.id),a,b,"1","classic-online-v1",match.participantA.seed()^match.participantB.seed(),2000,900000,200,p.minimumTrust());
        UUID job=queue.enqueue(spec);var routes=db.queryForList("select match_attempt from worker_pool_routes where job_id=?",job);
        if(routes.isEmpty())db.update("insert into worker_pool_routes(job_id,match_attempt) values(?,?)",job,match.attempt);
        else if(match.attempt>((Number)routes.getFirst().get("match_attempt")).intValue()&&db.queryForObject("select count(*) from match_execution_jobs where id=? and status='CANCELLED'",Integer.class,job)==1&&db.queryForObject("select count(*) from worker_result_receipts where job_id=?",Integer.class,job)==0){
            int floor=db.queryForObject("select count(*) from match_execution_attempts where job_id=?",Integer.class,job);
            db.update("update worker_pool_routes set match_attempt=?,retry_floor=? where job_id=?",match.attempt,floor,job);db.update("update match_execution_jobs set status='QUEUED' where id=?",job);
            db.update("update worker_execution_recoveries set projected_at=coalesce(projected_at,CURRENT_TIMESTAMP) where job_id=? and terminal=true",job);
        }
        return true;
    }
}
