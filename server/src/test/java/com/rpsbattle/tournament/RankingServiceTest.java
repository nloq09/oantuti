package com.rpsbattle.tournament;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.scheduler.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RankingServiceTest {
    @Test void liveRankingIgnoresPendingFailuresAndRecomputesWithoutDoubleCounting(){var t=TournamentFixtures.tournament(3);t.matches.add(new com.rpsbattle.tournament.domain.TournamentMatch("01",t.id,t.participants.get(0),t.participants.get(1)));t.matches.add(new com.rpsbattle.tournament.domain.TournamentMatch("02",t.id,t.participants.get(0),t.participants.get(2)));TournamentFixtures.finish(t.matches.get(0),"P0",12,5);TournamentFixtures.finish(t.matches.get(1),null,7,7);var service=new RankingService(new ClassicScoringRule());var rows=service.rank(t);assertThat(rows.get(0).participantId()).isEqualTo("P0");assertThat(rows.get(0).points()).isEqualTo(4);assertThat(rows.get(0).played()).isEqualTo(2);assertThat(rows.get(0).piecesRemaining()).isEqualTo(19);assertThat(rows.get(1).participantId()).isEqualTo("P2");assertThat(service.rank(t)).isEqualTo(rows);}
    @Test void stableTieBreakUsesRemainingPiecesThenParticipantId(){var t=TournamentFixtures.tournament(2);t.matches.addAll(new RoundRobinScheduler().schedule(t));TournamentFixtures.finish(t.matches.getFirst(),null,7,9);var s=new RankingService(new ClassicScoringRule());assertThat(s.rank(t).getFirst().participantId()).isEqualTo("P1");t.matches.getFirst().remainingA=9;assertThat(s.rank(t).getFirst().participantId()).isEqualTo("P0");}

    @Test void committedReadRankingIsCachedPerRevisionButUncommittedAndRolledBackReadsAreNot(){
        var t=TournamentFixtures.tournament(2);t.ownerUserId=java.util.UUID.randomUUID();t.matches.addAll(new RoundRobinScheduler().schedule(t));
        var ranks=org.mockito.Mockito.spy(new RankingService(new ClassicScoringRule()));var service=new TournamentService(null,null,null,ranks);
        var first=service.ranking(t);assertThat(service.ranking(t)).isSameAs(first);org.mockito.Mockito.verify(ranks,org.mockito.Mockito.times(1)).rank(t);
        t.deferNotifications=true;TournamentFixtures.finish(t.matches.getFirst(),"P0",9,1);assertThat(service.ranking(t).getFirst().points()).isEqualTo(3);t.matches.getFirst().status=com.rpsbattle.tournament.domain.TournamentMatch.Status.PENDING;t.deferNotifications=false;assertThat(service.ranking(t)).isSameAs(first);
        TournamentFixtures.finish(t.matches.getFirst(),"P1",1,9);service.changed(t);assertThat(service.ranking(t).getFirst().participantId()).isEqualTo("P1");assertThat(service.ranking(t)).isNotSameAs(first);
    }

    @Test void largeRosterAggregateCountsEveryFinishedGameOnceAndRetainsAllPlayers(){
        var t=TournamentFixtures.tournament(2000);t.tieBreakOrder=java.util.List.of(TieBreak.POINTS);for(int i=1;i<2000;i++){var m=new com.rpsbattle.tournament.domain.TournamentMatch("m"+i,t.id,t.participants.getFirst(),t.participants.get(i));TournamentFixtures.finish(m,"P0",9,1);t.matches.add(m);}var rows=new RankingService(new ClassicScoringRule()).rank(t);assertThat(rows).hasSize(2000);assertThat(rows.getFirst().participantId()).isEqualTo("P0");assertThat(rows.getFirst().wins()).isEqualTo(1999);assertThat(rows.getFirst().points()).isEqualTo(5997);assertThat(rows.stream().mapToInt(RankingEntry::played).sum()).isEqualTo(3998);
    }
}
