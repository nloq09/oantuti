package com.rpsbattle.tournament.ranking;
import com.rpsbattle.tournament.domain.*;
import java.util.*;

/** Refine tied groups in order. A mini-league avoids non-transitive pairwise comparisons. */
public final class TieBreakService {
    private TieBreakService() {}
    public static List<RankingEntry> order(Tournament t,List<RankingEntry> rows,ScoringRule scoring) {
        return refine(t,rows,scoring,0);
    }
    private static List<RankingEntry> refine(Tournament t,List<RankingEntry> rows,ScoringRule scoring,int criterion) {
        if(rows.size()<2)return rows;
        if(criterion>=t.tieBreakOrder.size())return rows.stream().sorted(Comparator.comparing(RankingEntry::participantId)).toList();
        var rule=t.tieBreakOrder.get(criterion);var ids=new HashSet<String>();rows.forEach(r->ids.add(r.participantId()));
        var groups=new TreeMap<Double,List<RankingEntry>>(Comparator.reverseOrder());
        for(var r:rows) {
            double score=switch(rule) {
                case POINTS->r.points();case WINS->r.wins();case REMAINING_PIECES->r.piecesRemaining();
                case AVERAGE_MOVE_TIME->r.averageMoveMillis()==null?Double.NEGATIVE_INFINITY:-r.averageMoveMillis();
                case HEAD_TO_HEAD->headToHead(t,r.participantId(),ids,scoring);
            };
            groups.computeIfAbsent(score,key->new ArrayList<>()).add(r);
        }
        var result=new ArrayList<RankingEntry>();for(var group:groups.values())result.addAll(refine(t,group,scoring,criterion+1));return result;
    }
    private static int headToHead(Tournament t,String id,Set<String> group,ScoringRule scoring) {
        int points=0;
        for(var m:t.matches)if(m.status==TournamentMatch.Status.FINISHED && group.contains(m.participantA.participantId()) && group.contains(m.participantB.participantId()) && (id.equals(m.participantA.participantId())||id.equals(m.participantB.participantId())))
            points+="DOUBLE_FORFEIT".equals(m.decisionReason)?scoring.pointsForLoss():m.winnerParticipantId==null?scoring.pointsForDraw():id.equals(m.winnerParticipantId)?scoring.pointsForWin():scoring.pointsForLoss();
        return points;
    }
}
