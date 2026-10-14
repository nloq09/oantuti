package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.UUID;
public final class TournamentAudit {
    private TournamentAudit() {}
    public static void append(Tournament t,String type,UUID actor,String target,String reason,String before,String after){
        t.operations=t.operations.append(new TournamentAuditEvent(UUID.randomUUID(),t.operations.audit().size()+1L,type,actor,target,reason,before,after,Instant.now()));
    }
    public static String result(TournamentMatch m){return m.status+"|"+m.result+"|"+m.winnerParticipantId+"|"+m.failure+"|"+m.historyMatchId+"|attempt="+m.attempt;}
}
