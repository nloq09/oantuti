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
            var totals=new HashMap<String,Totals>();
            for(var p:t.participants)totals.put(p.participantId(),new Totals());
            for(var m:TournamentSeries.scored(t))if(m.status==TournamentMatch.Status.FINISHED){
                add(totals.get(m.participantA.participantId()),m,true);
                add(totals.get(m.participantB.participantId()),m,false);
            }
            if(t.format==TournamentFormat.SWISS)for(var round:t.rounds)for(var id:round.byeParticipantIds()){
                var total=totals.get(id);if(total!=null){total.played++;total.wins++;}
            }
            var rows=new ArrayList<RankingEntry>();
            for(var p:t.participants){var total=totals.get(p.participantId());
                rows.add(new RankingEntry(0,p.participantId(),p.displayName(),total.played,total.wins,total.draws,total.losses,
                    total.wins*scoring.pointsForWin()+total.draws*scoring.pointsForDraw()+total.losses*scoring.pointsForLoss(),total.pieces,total.moves==0?null:total.nanos/(1_000_000.0*total.moves)));
            }
            var ordered=TieBreakService.order(t,rows,scoring);
            if(com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.hasKnockout(t)) {
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
    private static final class Totals {int played,wins,draws,losses,pieces,moves;long nanos;}
    private static void add(Totals total,TournamentMatch match,boolean a){
        if(total==null)return;total.played++;total.pieces+=a?match.remainingA:match.remainingB;total.moves+=a?match.moveCountA:match.moveCountB;total.nanos+=a?match.moveNanosA:match.moveNanosB;
        String id=a?match.participantA.participantId():match.participantB.participantId();
        if("DOUBLE_FORFEIT".equals(match.decisionReason))total.losses++;else if(match.winnerParticipantId==null)total.draws++;else if(id.equals(match.winnerParticipantId))total.wins++;else total.losses++;
    }
}
