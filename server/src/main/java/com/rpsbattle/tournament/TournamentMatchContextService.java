package com.rpsbattle.tournament;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only context for the existing BO1 match lifecycle. Never creates a room. */
@Service
public class TournamentMatchContextService {
    private final TournamentService tournaments;
    public TournamentMatchContextService(TournamentService tournaments) { this.tournaments=tournaments; }
    public Map<String,Object> context(String id,String onlineId) {
        var t=tournaments.find(id);
        synchronized(t) {
            var m=t.matches.stream().filter(game->onlineId.equals(game.onlineMatchId)).findFirst()
                .orElseThrow(()->new AuthException("TOURNAMENT_MATCH_NOT_FOUND",404));
            var result=new LinkedHashMap<String,Object>();
            result.put("tournamentId",t.id);result.put("tournamentName",t.name);result.put("tournamentStatus",t.status);
            result.put("revision",t.revision);result.put("matchId",m.id);result.put("onlineMatchId",m.onlineMatchId);
            result.put("stage",t.stages.getFirst());result.put("round",m.round);result.put("totalRounds",t.rounds.size());
            result.put("format",t.format);result.put("bestOf",1);result.put("gameNumber",1);
            result.put("status",m.status);result.put("winnerParticipantId",m.winnerParticipantId);
            result.put("participantA",Map.of("id",m.participantA.participantId(),"name",m.participantA.displayName()));
            result.put("participantB",Map.of("id",m.participantB.participantId(),"name",m.participantB.displayName()));
            result.put("historyMatchId",m.historyMatchId);
            return result;
        }
    }
}
