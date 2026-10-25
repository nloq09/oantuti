package com.rpsbattle.worker.remote;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.TournamentAuthorizationService;
import com.rpsbattle.tournament.coding.SubmissionStore;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentWorkerService {
    private final JdbcTemplate db;private final Clock clock;private final TournamentRegistry tournaments;
    private final TournamentAuthorizationService authorization;private final AccountAuthorizationService accounts;
    private final WorkerIdentityService identities;private final WorkerTelemetryService telemetry;private final WorkerControlService controls;
    private final SubmissionStore submissions;private final RemoteRoutingService routing;
    public TournamentWorkerService(JdbcTemplate db,Clock clock,TournamentRegistry tournaments,TournamentAuthorizationService authorization,AccountAuthorizationService accounts,WorkerIdentityService identities,WorkerTelemetryService telemetry,WorkerControlService controls,SubmissionStore submissions,RemoteRoutingService routing){this.db=db;this.clock=clock;this.tournaments=tournaments;this.authorization=authorization;this.accounts=accounts;this.identities=identities;this.telemetry=telemetry;this.controls=controls;this.submissions=submissions;this.routing=routing;}
    public record Machine(UUID id,String name,String status,long revision,int freeSlots,int activeMatches,Set<String> languages,Instant lastHeartbeatAt,boolean revoked){}
    public record Token(UUID id,Instant expiresAt,int remainingUses,boolean revoked){}
    public record Snapshot(List<Machine> workers,List<Token> tokens,boolean canAdd,String reason,String executionMode,Instant serverNow){}
    private Tournament managed(Authentication auth,String id){var t=tournaments.findById(id).orElseThrow(()->new AuthException("TOURNAMENT_NOT_FOUND",404));authorization.requireManager(auth,t);return t;}
    private String unavailable(Tournament t){return t.archived||Set.of(TournamentStatus.CANCELLED,TournamentStatus.FINISHED).contains(t.status)?"Giải đã kết thúc hoặc hủy; không thể thêm máy mới.":t.participantPolicy!=ParticipantPolicy.BOT_ONLY||!submissions.enabled(t.id)?"Worker riêng hỗ trợ giải chỉ bot sử dụng bài coding đã khóa.":"";}
    private void ownWorker(String tournament,UUID worker){if(db.queryForObject("select count(*) from worker_tournament_scopes where worker_id=? and tournament_id=?",Integer.class,worker,UUID.fromString(tournament))!=1)throw new AuthException("WORKER_NOT_FOUND",404);}
    private Machine machine(UUID id){var w=telemetry.view(id);return new Machine(w.id(),w.name(),w.status(),w.revision(),w.credentialRevoked()?0:w.effectiveFreeSlots(),w.reservedSlots(),w.capabilities()==null?Set.of():w.capabilities().supportedLanguages(),w.lastHeartbeatAt(),w.credentialRevoked());}
    @Transactional(readOnly=true) public Snapshot list(Authentication auth,String id){
        var t=managed(auth,id);var workers=db.query("select s.worker_id from worker_tournament_scopes s join worker_credentials c on c.worker_id=s.worker_id where s.tournament_id=? order by case when c.revoked_at is null then 0 else 1 end,s.created_at desc,s.worker_id limit 100",(r,n)->r.getObject(1,UUID.class),UUID.fromString(id)).stream().map(this::machine).toList();
        var tokens=db.query("select t.id,t.expires_at,t.remaining_uses,t.revoked_at from worker_registration_tokens t join worker_token_scopes s on s.token_id=t.id where s.tournament_id=? order by t.created_at desc,t.id limit 20",(r,n)->new Token(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getInt(3),r.getTimestamp(4)!=null),UUID.fromString(id));
        String reason=unavailable(t);if(reason.isEmpty()&&(workers.stream().filter(w->!w.revoked()).count()>=20||tokens.stream().filter(token->!token.revoked()&&token.remainingUses()>0&&clock.instant().isBefore(token.expiresAt())).count()>=5))reason="Giải đã đủ 20 máy còn quyền hoặc 5 token đang có hiệu lực. Thu hồi mục không còn dùng để thêm máy.";return new Snapshot(workers,tokens,reason.isEmpty(),reason,routing.policy(id).mode(),clock.instant());
    }
    @Transactional public WorkerIdentityService.Token issue(Authentication auth,String id,boolean trusted){
        var t=managed(auth,id);if(!trusted)throw new AuthException("WORKER_TRUST_CONSENT_REQUIRED",400);
        synchronized(t){String reason=unavailable(t);if(!reason.isEmpty())throw new AuthException("TOURNAMENT_WORKERS_UNAVAILABLE",409);
            db.queryForObject("select id from remote_match_queue_guard where id=1 for update",Integer.class);UUID key=UUID.fromString(id);
            if(db.queryForObject("select count(*) from worker_tournament_scopes s join worker_credentials c on c.worker_id=s.worker_id where s.tournament_id=? and c.revoked_at is null",Integer.class,key)>=20||db.queryForObject("select count(*) from worker_token_scopes s join worker_registration_tokens t on t.id=s.token_id where s.tournament_id=? and t.remaining_uses>0 and t.revoked_at is null and t.expires_at>?",Integer.class,key,java.sql.Timestamp.from(clock.instant()))>=5)throw new AuthException("TOURNAMENT_WORKER_LIMIT",409);
            var token=identities.issue(accounts.requireUser(auth).id().value(),1,30);db.update("insert into worker_token_scopes(token_id,tournament_id) values(?,?)",token.id(),key);return token;
        }
    }
    @Transactional public void revokeToken(Authentication auth,String id,UUID token){
        managed(auth,id);if(db.queryForObject("select count(*) from worker_token_scopes where token_id=? and tournament_id=?",Integer.class,token,UUID.fromString(id))!=1)throw new AuthException("WORKER_TOKEN_NOT_FOUND",404);identities.revokeToken(token,accounts.requireUser(auth).id().value());
    }
    @Transactional public Machine control(Authentication auth,String id,UUID worker,WorkerControlService.Command command){
        managed(auth,id);ownWorker(id,worker);controls.control(worker,accounts.requireUser(auth).id().value(),command);return machine(worker);
    }
    public void downloadAllowed(Authentication auth,String id){managed(auth,id);}
}
