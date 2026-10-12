package com.rpsbattle.tournament.ranking;
import com.rpsbattle.tournament.domain.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class RankingService {
    private final ScoringRule scoring;
    public RankingService(ScoringRule scoring) { this.scoring=scoring; }
    public List<RankingEntry> rank(Tournament t) {
        synchronized(t) {
            var rows=new ArrayList<RankingEntry>();
            for(var p:t.participants) {
                int played=0,wins=0,draws=0,losses=0,pieces=0;
                for(var m:t.matches) if(m.status==TournamentMatch.Status.FINISHED &&
                        (m.participantA.equals(p)||m.participantB.equals(p))) {
                    played++; pieces+=m.participantA.equals(p)?m.remainingA:m.remainingB;
                    if(m.winnerParticipantId==null) draws++;
                    else if(m.winnerParticipantId.equals(p.participantId())) wins++; else losses++;
                }
                rows.add(new RankingEntry(0,p.participantId(),p.displayName(),played,wins,draws,losses,
                    wins*scoring.pointsForWin()+draws*scoring.pointsForDraw()+losses*scoring.pointsForLoss(),pieces));
            }
            rows.sort(Comparator.comparingInt(RankingEntry::points).reversed()
                .thenComparing(Comparator.comparingInt(RankingEntry::wins).reversed())
                .thenComparing(Comparator.comparingInt(RankingEntry::piecesRemaining).reversed()).thenComparing(RankingEntry::participantId));
            var result=new ArrayList<RankingEntry>();
            for(var r:rows) result.add(new RankingEntry(result.size()+1,r.participantId(),r.displayName(),r.played(),r.wins(),r.draws(),r.losses(),r.points(),r.piecesRemaining()));
            return List.copyOf(result);
        }
    }
}
