package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import java.util.*;
public class RoundRobinScheduler implements TournamentScheduler {
    public List<TournamentMatch> schedule(Tournament t) {
        var rotation=new ArrayList<>(t.participants);if(rotation.size()%2!=0)rotation.add(null);
        var result=new ArrayList<TournamentMatch>();t.rounds.clear();
        for(int round=1;round<rotation.size();round++) {
            var byes=new ArrayList<String>();
            for(int slot=0;slot<rotation.size()/2;slot++) {
                var a=rotation.get(slot);var b=rotation.get(rotation.size()-1-slot);
                if(a==null||b==null){byes.add((a==null?b:a).participantId());continue;}
                if((round+slot)%2==0){var swap=a;a=b;b=swap;}
                var match=new TournamentMatch(UUID.nameUUIDFromBytes((t.id+"/"+round+"/"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),t.id,a,b);
                match.round=round;match.scheduledAt=java.time.Instant.now();result.add(match);
            }
            t.rounds.add(new TournamentRound(round,byes));rotation.add(1,rotation.removeLast());
        }
        return List.copyOf(result);
    }
}
