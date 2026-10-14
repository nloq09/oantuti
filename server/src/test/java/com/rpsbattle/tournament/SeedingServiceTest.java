package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.seeding.SeedingService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SeedingServiceTest {
    List<TournamentParticipant> roster(){var result=new ArrayList<TournamentParticipant>();for(int i=0;i<8;i++)result.add(new TournamentParticipant("p"+i,"b"+i,"secret","Bot "+i,42));return result;}
    @Test void randomSeedingIsIndependentOfInputOrderAndPreservesBotRandomSeed() {
        var list=roster();var shuffled=new ArrayList<>(list);Collections.reverse(shuffled);
        var first=SeedingService.seed(list,SeedingStrategy.RANDOM,123,Map.of());
        assertThat(SeedingService.seed(shuffled,SeedingStrategy.RANDOM,123,Map.of())).isEqualTo(first);
        assertThat(first).extracting(TournamentParticipant::seedNumber).containsExactly(1,2,3,4,5,6,7,8);assertThat(first).allMatch(p->p.seed()==42);
        assertThat(SeedingService.seed(list,SeedingStrategy.RANDOM,456,Map.of())).isNotEqualTo(first);assertThat(list).allMatch(p->p.seedNumber()==null);
    }
    @Test void manualRejectsMissingDuplicateAndRanksCheckedInSubsetWithoutGaps() {
        assertThatThrownBy(()->SeedingService.seed(roster(),SeedingStrategy.MANUAL,0,Map.of())).hasMessage("INVALID_MANUAL_SEEDS");
        var p=roster().get(0).withSeeding(null,3);var q=roster().get(1).withSeeding(null,1);
        assertThat(SeedingService.seed(List.of(p,q),SeedingStrategy.MANUAL,0,Map.of())).extracting(TournamentParticipant::participantId).containsExactly("p1","p0");
        assertThatThrownBy(()->SeedingService.seed(List.of(p,p),SeedingStrategy.MANUAL,0,Map.of())).hasMessage("INVALID_MANUAL_SEEDS");
    }
    @Test void rankingUsesPointsWinsPiecesAndStableIdentityForUnknownBots() {
        var seeded=SeedingService.seed(roster(),SeedingStrategy.RANKING,0,Map.of("b7",new SeedingService.Score(5,1,8),"b6",new SeedingService.Score(5,2,4),"b5",new SeedingService.Score(5,2,9)));
        assertThat(seeded).extracting(TournamentParticipant::participantId).containsExactly("p5","p6","p7","p0","p1","p2","p3","p4");
    }
}
