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
                int played=0,wins=0,draws=0,losses=0,pieces=0,moves=0;long nanos=0;
                for(var m:TournamentSeries.scored(t)) if(m.status==TournamentMatch.Status.FINISHED &&
                        (m.participantA.participantId().equals(p.participantId())||m.participantB.participantId().equals(p.participantId()))) {
                    boolean a=m.participantA.participantId().equals(p.participantId());
                    played++;pieces+=a?m.remainingA:m.remainingB;moves+=a?m.moveCountA:m.moveCountB;nanos+=a?m.moveNanosA:m.moveNanosB;
                    if("DOUBLE_FORFEIT".equals(m.decisionReason))losses++;else if(m.winnerParticipantId==null) draws++;
                    else if(m.winnerParticipantId.equals(p.participantId())) wins++; else losses++;
                }
                rows.add(new RankingEntry(0,p.participantId(),p.displayName(),played,wins,draws,losses,
                    wins*scoring.pointsForWin()+draws*scoring.pointsForDraw()+losses*scoring.pointsForLoss(),pieces,moves==0?null:nanos/(1_000_000.0*moves)));
            }
            var ordered=TieBreakService.order(t,rows,scoring);
            if(t.format==TournamentFormat.SINGLE_ELIMINATION) {
                // Bracket progression precedes statistical ties, so a BYE never penalizes a finalist.
                var progress=new HashMap<String,Integer>();
                for(var n:t.bracket) {
                    if(n.slotA()!=null)progress.merge(n.slotA(),n.round()*2,Math::max);
                    if(n.slotB()!=null)progress.merge(n.slotB(),n.round()*2,Math::max);
                    if(n.winnerParticipantId()!=null)progress.merge(n.winnerParticipantId(),n.round()*2+1,Math::max);
                }
                ordered=ordered.stream().sorted(Comparator.comparingInt((RankingEntry r)->progress.getOrDefault(r.participantId(),0)).reversed()).toList();
            }
            ordered=ordered.stream().sorted(Comparator.comparing((RankingEntry r)->!t.operations.active(r.participantId()))).toList();
            var result=new ArrayList<RankingEntry>();
            for(var r:ordered) result.add(new RankingEntry(result.size()+1,r.participantId(),r.displayName(),r.played(),r.wins(),r.draws(),r.losses(),r.points(),r.piecesRemaining(),r.averageMoveMillis()));
            return List.copyOf(result);
        }
    }
}
