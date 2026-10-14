package com.rpsbattle.tournament.seeding;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.online.DemoRequestException;
import java.util.*;

/** Seed numbers order the tournament; participant.seed remains the bot's existing RNG seed. */
public final class SeedingService {
    public record Score(long points,long wins,long pieces) {public Score plus(Score other){return new Score(points+other.points,wins+other.wins,pieces+other.pieces);}}
    private SeedingService() {}
    public static List<TournamentParticipant> seed(List<TournamentParticipant> roster,SeedingStrategy strategy,long randomSeed,Map<String,Score> scores) {
        var ordered=new ArrayList<>(roster);ordered.sort(Comparator.comparing(TournamentParticipant::participantId));
        switch(strategy) {
            case RANDOM -> Collections.shuffle(ordered,new Random(randomSeed));
            case MANUAL -> {
                var values=new HashSet<Integer>();
                for(var p:ordered)if(p.manualSeed()==null || p.manualSeed()<1 || !values.add(p.manualSeed()))throw new DemoRequestException("INVALID_MANUAL_SEEDS");
                ordered.sort(Comparator.comparing(TournamentParticipant::manualSeed));
            }
            case RANKING -> ordered.sort(Comparator.<TournamentParticipant>comparingLong(p->scores.getOrDefault(p.botId(),new Score(0,0,0)).points()).reversed()
                .thenComparing(Comparator.<TournamentParticipant>comparingLong(p->scores.getOrDefault(p.botId(),new Score(0,0,0)).wins()).reversed())
                .thenComparing(Comparator.<TournamentParticipant>comparingLong(p->scores.getOrDefault(p.botId(),new Score(0,0,0)).pieces()).reversed()).thenComparing(TournamentParticipant::participantId));
        }
        var result=new ArrayList<TournamentParticipant>();for(var p:ordered)result.add(p.withSeeding(result.size()+1,p.manualSeed()));return List.copyOf(result);
    }
}
