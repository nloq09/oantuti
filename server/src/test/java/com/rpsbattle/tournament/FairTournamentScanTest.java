package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FairTournamentScanTest {
 @Test void everyCupGetsFirstAdmissionEvenWhenPreviousCupsAlwaysFail(){var scan=new FairTournamentScan();var cups=new ArrayList<Tournament>();for(int i=0;i<100;i++)cups.add(new Tournament(String.format("%03d",i),"Cup",TournamentFormat.SINGLE_ELIMINATION,Instant.EPOCH));var served=new HashSet<String>();for(int i=0;i<100;i++)served.add(scan.next(cups).getFirst().id);assertThat(served).hasSize(100);assertThat(scan.next(List.of())).isEmpty();assertThat(scan.next(cups.subList(0,2))).hasSize(2);}
}
