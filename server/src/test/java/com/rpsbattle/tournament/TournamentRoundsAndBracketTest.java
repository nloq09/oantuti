package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.scheduler.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class TournamentRoundsAndBracketTest {
    static Tournament knockout(int n) {var t=new Tournament("knockout","Cup",TournamentFormat.SINGLE_ELIMINATION,Instant.EPOCH);t.participants.addAll(TournamentFixtures.tournament(n).participants);return t;}
    @ParameterizedTest @ValueSource(ints={2,3,4,5,6,8,10,16,32})
    void roundRobinHasEveryUnorderedPairOnceAndEachBotPlaysAtMostOncePerRound(int n) {
        var t=TournamentFixtures.tournament(n);var games=new RoundRobinScheduler().schedule(t);
        assertThat(games).hasSize(n*(n-1)/2);assertThat(t.rounds).hasSize(n%2==0?n-1:n);
        var pairs=new HashSet<Set<String>>();
        for(var round:t.rounds) {
            var seen=new HashSet<String>();
            for(var m:games)if(m.round==round.number()) {assertThat(seen.add(m.participantA.participantId())).isTrue();assertThat(seen.add(m.participantB.participantId())).isTrue();assertThat(pairs.add(Set.of(m.participantA.participantId(),m.participantB.participantId()))).isTrue();assertThat(m.scheduledAt).isNotNull();}
            for(var bye:round.byeParticipantIds())assertThat(seen.add(bye)).isTrue();assertThat(seen).hasSize(n);
            assertThat(round.byeParticipantIds()).hasSize(n%2);
        }
        if(n%2==1)assertThat(t.rounds.stream().flatMap(r->r.byeParticipantIds().stream()).distinct()).hasSize(n);
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,5,6,8,16,32})
    void knockoutAdvancesIdempotentlyWithExactlyNMinusOneRealGames(int n) {
        var t=knockout(n);t.matches.addAll(new SingleEliminationScheduler().schedule(t));
        int size=2;while(size<n)size*=2;
        assertThat(t.bracket.stream().filter(b->"BYE".equals(b.resolution()))).hasSize(size-n);
        assertThat(t.bracket.stream().map(BracketNode::matchId).filter(Objects::nonNull).distinct()).hasSize(n-1);
        for(int tick=0;tick<6&&!SingleEliminationScheduler.complete(t);tick++) {
            for(var m:t.matches)if(m.status==TournamentMatch.Status.PENDING)TournamentFixtures.finish(m,m.participantA.participantId(),10,0);
            t.matches.addAll(SingleEliminationScheduler.advance(t));assertThat(SingleEliminationScheduler.advance(t)).isEmpty();
        }
        assertThat(SingleEliminationScheduler.complete(t)).isTrue();assertThat(t.matches).hasSize(n-1).allMatch(m->m.status==TournamentMatch.Status.FINISHED);
        assertThat(t.matches.stream().map(m->m.id).distinct()).hasSize(n-1);
        assertThat(new RankingService(new ClassicScoringRule()).rank(t).getFirst().participantId()).isEqualTo(t.bracket.getLast().winnerParticipantId());
        for(var m:t.matches)assertThat(m.participantA).isNotEqualTo(m.participantB);
    }
    @Test void unfinishedAndInfrastructureFailedNodesNeverPromoteAPlayer() {
        var t=knockout(4);t.matches.addAll(new SingleEliminationScheduler().schedule(t));
        TournamentFixtures.finish(t.matches.getFirst(),t.matches.getFirst().participantA.participantId(),5,0);
        t.matches.getLast().status=TournamentMatch.Status.FAILED;
        assertThat(SingleEliminationScheduler.advance(t)).isEmpty();assertThat(t.bracket.getLast().winnerParticipantId()).isNull();assertThat(t.bracket.getLast().slotB()).isNull();
    }
    @Test void drawPolicyPreservesDrawScoreAndRecordsReasonForAdvancement() {
        var t=knockout(2);t.matches.addAll(new SingleEliminationScheduler().schedule(t));var m=t.matches.getFirst();
        TournamentFixtures.finish(m,null,3,7);SingleEliminationScheduler.advance(t);
        assertThat(t.bracket.getLast().winnerParticipantId()).isEqualTo(m.participantB.participantId());assertThat(t.bracket.getLast().resolution()).isEqualTo("DRAW_REMAINING_PIECES");
        assertThat(new RankingService(new ClassicScoringRule()).rank(t)).allMatch(r->r.points()==1&&r.draws()==1&&r.wins()==0);
        var tied=knockout(2);tied.matches.addAll(new SingleEliminationScheduler().schedule(tied));TournamentFixtures.finish(tied.matches.getFirst(),null,7,7);SingleEliminationScheduler.advance(tied);
        assertThat(tied.bracket.getLast().resolution()).isEqualTo("DRAW_SEED");assertThat(tied.bracket.getLast().winnerParticipantId()).isEqualTo(tied.participants.getFirst().participantId());
    }
    @Test void structureSnapshotIsImmutableAndJsonRoundTripPreservesFutureSlotsAndMetrics() {
        var t=knockout(6);t.maxConcurrentMatches=1;t.matches.addAll(new SingleEliminationScheduler().schedule(t));
        t.matches.getFirst().moveCountA=2;t.matches.getFirst().moveNanosA=123456789;
        var snapshot=TournamentDetails.snapshot(t);var converter=new TournamentDetailsConverter();
        var decoded=converter.convertToEntityAttribute(converter.convertToDatabaseColumn(snapshot));assertThat(decoded).isEqualTo(snapshot);
        t.bracket.clear();decoded.restore(t);assertThat(t.bracket).hasSize(7);assertThat(t.maxConcurrentMatches).isEqualTo(1);assertThat(t.matches.getFirst().moveNanosA).isEqualTo(123456789);
    }
}
