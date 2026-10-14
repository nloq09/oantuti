package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TieBreakServiceTest {
    private final RankingService ranking=new RankingService(new ClassicScoringRule());
    private TournamentMatch game(Tournament t,int a,int b,Integer winner,int remainingA,int remainingB) {
        var m=new TournamentMatch(UUID.randomUUID().toString(),t.id,t.participants.get(a),t.participants.get(b));t.matches.add(m);
        TournamentFixtures.finish(m,winner==null?null:t.participants.get(winner).participantId(),remainingA,remainingB);return m;
    }
    @Test void headToHeadUsesMiniLeagueForThreeWayCycleAndThenNextCriterion() {
        var t=TournamentFixtures.tournament(3);game(t,0,1,0,5,0);game(t,1,2,1,7,0);game(t,2,0,2,9,0);
        assertThat(ranking.rank(t)).extracting(RankingEntry::participantId).containsExactly("P2","P1","P0");
        t.tieBreakOrder=List.of(TieBreak.POINTS,TieBreak.HEAD_TO_HEAD);
        assertThat(ranking.rank(t)).extracting(RankingEntry::participantId).containsExactly("P0","P1","P2");
    }
    @Test void headToHeadBeatsSurvivingPiecesAndChangingOrderChangesResult() {
        var t=TournamentFixtures.tournament(4);game(t,0,1,0,1,0);game(t,0,2,2,0,2);game(t,1,2,1,18,0);
        // P0/P1/P2 all have 3 points; separate P2 using a draw against P3.
        game(t,2,3,null,3,3);
        assertThat(ranking.rank(t)).extracting(RankingEntry::participantId).containsExactly("P2","P0","P1","P3");
        t.tieBreakOrder=List.of(TieBreak.POINTS,TieBreak.REMAINING_PIECES,TieBreak.HEAD_TO_HEAD);
        assertThat(ranking.rank(t)).extracting(RankingEntry::participantId).containsExactly("P2","P1","P0","P3");
    }
    @Test void averageTimeIsWeightedByMovesAndMissingSamplesDoNotLookFast() {
        var t=TournamentFixtures.tournament(3);var a=game(t,0,1,null,5,5);var b=game(t,0,2,null,5,5);var c=game(t,1,2,null,5,5);
        a.moveCountA=1;a.moveNanosA=100000000;a.moveCountB=2;a.moveNanosB=80000000;
        b.moveCountA=9;b.moveNanosA=90000000;c.moveCountA=2;c.moveNanosA=80000000;
        t.tieBreakOrder=List.of(TieBreak.POINTS,TieBreak.AVERAGE_MOVE_TIME);
        var rows=ranking.rank(t);assertThat(rows).extracting(RankingEntry::participantId).containsExactly("P0","P1","P2");
        assertThat(rows.getFirst().averageMoveMillis()).isEqualTo(19.0);assertThat(rows.getLast().averageMoveMillis()).isNull();
        a.status=TournamentMatch.Status.FAILED;assertThat(ranking.rank(t).stream().mapToInt(RankingEntry::played).sum()).isEqualTo(4);
    }
}
