package com.rpsbattle.tournament;
import com.rpsbattle.tournament.scheduler.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class RoundRobinSchedulerTest {
    @ParameterizedTest @ValueSource(ints={2,3,4,5,10}) void uniquePairsWithoutSelfMatches(int n){var list=new RoundRobinScheduler().schedule(TournamentFixtures.tournament(n));assertThat(list).hasSize(n*(n-1)/2);assertThat(list).allMatch(m->!m.participantA.equals(m.participantB));assertThat(list.stream().map(m->m.participantA.participantId()+"/"+m.participantB.participantId()).distinct()).hasSize(list.size());assertThat(list.stream().map(m->m.id).distinct()).hasSize(list.size());}
}
