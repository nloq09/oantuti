package com.rpsbattle.tournament;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.scheduler.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RankingServiceTest {
    @Test void liveRankingIgnoresPendingFailuresAndRecomputesWithoutDoubleCounting(){var t=TournamentFixtures.tournament(3);t.matches.addAll(new RoundRobinScheduler().schedule(t));TournamentFixtures.finish(t.matches.get(0),"P0",12,5);TournamentFixtures.finish(t.matches.get(1),null,7,7);var service=new RankingService(new ClassicScoringRule());var rows=service.rank(t);assertThat(rows.get(0).participantId()).isEqualTo("P0");assertThat(rows.get(0).points()).isEqualTo(4);assertThat(rows.get(0).played()).isEqualTo(2);assertThat(rows.get(0).piecesRemaining()).isEqualTo(19);assertThat(rows.get(1).participantId()).isEqualTo("P2");assertThat(service.rank(t)).isEqualTo(rows);}
    @Test void stableTieBreakUsesRemainingPiecesThenParticipantId(){var t=TournamentFixtures.tournament(2);t.matches.addAll(new RoundRobinScheduler().schedule(t));TournamentFixtures.finish(t.matches.getFirst(),null,7,9);var s=new RankingService(new ClassicScoringRule());assertThat(s.rank(t).getFirst().participantId()).isEqualTo("P1");t.matches.getFirst().remainingA=9;assertThat(s.rank(t).getFirst().participantId()).isEqualTo("P0");}
}
