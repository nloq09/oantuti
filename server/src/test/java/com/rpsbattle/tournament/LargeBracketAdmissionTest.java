package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.scheduler.SingleEliminationScheduler;
import com.rpsbattle.worker.ExecutionQueue;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LargeBracketAdmissionTest {
 @Test void twoThousandEntrantsHaveLinearBracketAndOnlyBoundedInitialReservations(){var t=new Tournament("large","Large",TournamentFormat.SINGLE_ELIMINATION,Instant.EPOCH);for(int i=0;i<2000;i++)t.participants.add(new TournamentParticipant("p"+i,null,null,"Player "+i,i).withSeeding(i+1,null));var matches=new SingleEliminationScheduler().schedule(t);assertThat(t.bracket).hasSize(2047);assertThat(matches.size()).isLessThanOrEqualTo(1000);var queue=new ExecutionQueue(256);queue.reserve(matches.stream().limit(4).map(m->m.id).toList());assertThat(queue.size()).isEqualTo(4);assertThat(t.bracket.stream().filter(n->n.resolution()!=null)).isNotEmpty();}
}
