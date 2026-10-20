package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.api.dto.OnlineSnapshot;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.replay.*;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Durable receipt -> existing match/history/replay; normal tournament tick advances rankings/brackets. */
@Service
public class RemoteResultProjector {
    private final JdbcTemplate db;private final ObjectMapper json;private final TournamentRegistry registry;
    private final TournamentService tournaments;private final TournamentPersistenceCoordinator persistence;
    private final ReplayArchiveStore archives;private final GameStateMapper mapper;private final TransactionTemplate tx;
    public RemoteResultProjector(JdbcTemplate db,ObjectMapper json,TournamentRegistry registry,TournamentService tournaments,TournamentPersistenceCoordinator persistence,ReplayArchiveStore archives,GameStateMapper mapper,PlatformTransactionManager manager){this.db=db;this.json=json;this.registry=registry;this.tournaments=tournaments;this.persistence=persistence;this.archives=archives;this.mapper=mapper;this.tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelayString="${rps.worker.result-project-millis:500}")
    public void synchronize(){for(var id:db.query("select execution_id from worker_result_receipts where projection_status='PENDING' order by received_at limit 32",(r,n)->r.getObject(1,UUID.class)))try{project(id);}catch(RuntimeException e){db.update("update worker_result_receipts set projection_error='PROJECTION_RETRY_REQUIRED' where execution_id=? and projection_status='PENDING'",id);}
        for(var id:db.query("select execution_id from worker_execution_recoveries where terminal=true and projected_at is null order by recorded_at limit 32",(r,n)->r.getObject(1,UUID.class)))try{projectFailure(id);}catch(RuntimeException ignored){/* Durable failure stays pending. */}
    }
    public void projectFailure(UUID execution){
        var rows=db.queryForList("select tournament_id,match_id from worker_execution_recoveries where execution_id=? and terminal=true and projected_at is null",execution);if(rows.isEmpty())return;
        var t=registry.findById(rows.getFirst().get("tournament_id").toString()).orElse(null);if(t==null)return;
        synchronized(t){
            var m=t.matches.stream().filter(v->v.id.equals(rows.getFirst().get("match_id").toString())).findFirst().orElse(null);if(m==null)return;
            var before=m.copy();var operations=t.operations;long revision=t.revision;boolean defer=t.deferNotifications;var oldStatus=t.status;var oldChanged=t.statusChangedAt;t.deferNotifications=true;
            try{tx.executeWithoutResult(status->{
                var record=db.queryForList("select * from worker_execution_recoveries where execution_id=? for update",execution).getFirst();if(record.get("projected_at")!=null)return;
                if(m.status==TournamentMatch.Status.PENDING&&Set.of(TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status)){
                    m.status=TournamentMatch.Status.FAILED;m.failure="REMOTE_WORKER_INFRASTRUCTURE_ERROR";m.finishedAt=Instant.now();m.winnerParticipantId=null;m.result=null;
                    if(t.status!=TournamentStatus.RECOVERY_REQUIRED)TournamentStateMachine.transition(t,TournamentStatus.RECOVERY_REQUIRED,Instant.now());
                    TournamentAudit.append(t,"MATCH_ERROR",null,m.id,"REMOTE_RETRY_EXHAUSTED",TournamentAudit.result(before),TournamentAudit.result(m));tournaments.changed(t);persistence.persistNow(t);
                }
                db.update("update worker_execution_recoveries set projected_at=CURRENT_TIMESTAMP where execution_id=?",execution);
            });}catch(RuntimeException e){m.restore(before);t.operations=operations;t.revision=revision;t.status=oldStatus;t.statusChangedAt=oldChanged;throw e;}finally{t.deferNotifications=defer;}
            tournaments.notifyChanged(t);
        }
    }
    public void project(UUID execution) {
        var rows=db.queryForList("select tournament_id from worker_result_receipts where execution_id=?",execution);if(rows.isEmpty())return;
        var t=registry.findById(rows.getFirst().get("tournament_id").toString()).orElse(null);if(t==null)return;
        synchronized(t) {
            var candidate=db.queryForList("select match_id from worker_result_receipts where execution_id=? and projection_status='PENDING'",execution);if(candidate.isEmpty())return;
            var m=t.matches.stream().filter(v->v.id.equals(candidate.getFirst().get("match_id").toString())).findFirst().orElse(null);if(m==null)return;
            var before=m.copy();var operations=t.operations;long revision=t.revision;boolean defer=t.deferNotifications;t.deferNotifications=true;
            try {
                tx.executeWithoutResult(status->{
                    var receipt=db.queryForList("select * from worker_result_receipts where execution_id=? for update",execution).getFirst();if(!"PENDING".equals(receipt.get("projection_status")))return;
                    if(!Set.of(TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status)||m.status!=TournamentMatch.Status.PENDING){db.update("update worker_result_receipts set projection_status='IGNORED',projection_error='MATCH_STATE_CHANGED',projected_at=CURRENT_TIMESTAMP where execution_id=?",execution);return;}
                    RemoteResultService.Result result;try{result=json.readValue(receipt.get("result_json").toString(),RemoteResultService.Result.class);}catch(Exception e){throw new IllegalStateException(e);}
                    var log=new ReplayService().read(receipt.get("replay_json").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));var state=new ReplayService().replay(log);
                    Object stamp=receipt.get("received_at");Instant finished=stamp instanceof java.sql.Timestamp s?s.toInstant():((OffsetDateTime)stamp).toInstant();Instant started=finished.minusMillis(result.durationMs());
                    var a=m.participantA;var b=m.participantB;String winner=result.winner();var winnerUser=winner==null?null:winner.equals("P1")?a.ownerUserId():b.ownerUserId();
                    var seats=List.of(new MatchMetadata.Participant("P1",a.ownerUserId(),UUID.fromString(a.botId()),MatchMetadata.ControllerType.UPLOADED_BOT),new MatchMetadata.Participant("P2",b.ownerUserId(),UUID.fromString(b.botId()),MatchMetadata.ControllerType.UPLOADED_BOT));
                    var history=new MatchMetadata(m.id,m.id,"TOURNAMENT_REMOTE","FINISHED",a.ownerUserId(),b.ownerUserId(),winnerUser,winner,state.result().orElseThrow().outcome().name(),state.turnNumber(),started,finished,result.durationMs(),log.ruleSet(),started,seats);
                    var players=List.of(new OnlineSnapshot.PlayerSeatDto("P1",true,false,true,true,a.displayName()),new OnlineSnapshot.PlayerSeatDto("P2",true,false,true,true,b.displayName()));
                    var snapshot=new OnlineSnapshot(mapper.toDto(state,List.of(),List.of()),"HUMAN_VS_HUMAN","FINISHED",null,players,Map.of(),List.of(),null,finished,started,finished,"BOT_FAILED".equals(result.reason())?"BOT_FAILED":null);
                    archives.persistRemote(history,log,snapshot);
                    m.historyMatchId=m.id;m.onlineMatchId=null;m.status=TournamentMatch.Status.FINISHED;m.winnerParticipantId=winner==null?null:winner.equals("P1")?a.participantId():b.participantId();m.result=state.result().orElseThrow().outcome().name();m.failure=snapshot.botError();m.remainingA=state.aliveCount(com.rpsbattle.online.OnlineMatch.P1);m.remainingB=state.aliveCount(com.rpsbattle.online.OnlineMatch.P2);m.startedAt=started;m.finishedAt=finished;
                    TournamentAudit.append(t,"MATCH_FINISHED",null,m.id,"REMOTE_WORKER_RESULT",TournamentAudit.result(before),TournamentAudit.result(m));tournaments.changed(t);persistence.persistNow(t);
                    db.update("update worker_result_receipts set projection_status='APPLIED',projected_at=CURRENT_TIMESTAMP,projection_error=null where execution_id=?",execution);
                });
            }catch(RuntimeException e){m.restore(before);t.operations=operations;t.revision=revision;throw e;}
            finally{t.deferNotifications=defer;}
            tournaments.notifyChanged(t);
        }
    }
}
