package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
final class TournamentFixtures {
    static Tournament tournament(int n){var t=new Tournament("T","Test",TournamentFormat.ROUND_ROBIN,Instant.EPOCH);for(int i=0;i<n;i++)t.participants.add(new TournamentParticipant("P"+i,"B"+i,"secret-"+i,"Bot "+i,i));return t;}
    static void finish(TournamentMatch m,String winner,int a,int b){m.status=TournamentMatch.Status.FINISHED;m.winnerParticipantId=winner;m.remainingA=a;m.remainingB=b;}
}
