package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Enrollment fixes the scope on Central; worker request bodies cannot choose or change it. */
public final class WorkerScope {
    private WorkerScope(){}
    public static final String JOB_FILTER="(not exists(select 1 from worker_tournament_scopes scope where scope.worker_id=?) or exists(select 1 from worker_tournament_scopes scope where scope.worker_id=? and scope.tournament_id=j.tournament_id))";
    public static boolean allows(JdbcTemplate db,UUID worker,UUID tournament){
        var scopes=db.query("select tournament_id from worker_tournament_scopes where worker_id=?",(r,n)->r.getObject(1,UUID.class),worker);
        return scopes.isEmpty()||scopes.getFirst().equals(tournament);
    }
    public static void require(JdbcTemplate db,UUID worker,UUID tournament){
        if(!allows(db,worker,tournament))throw new AuthException("WORKER_TOURNAMENT_FORBIDDEN",403);
    }
}
