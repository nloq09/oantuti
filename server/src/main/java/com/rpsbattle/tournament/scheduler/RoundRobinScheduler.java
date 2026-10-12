package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class RoundRobinScheduler implements TournamentScheduler {
    public List<TournamentMatch> schedule(Tournament t) {
        var result=new ArrayList<TournamentMatch>();
        for (int a=0;a<t.participants.size();a++) for(int b=a+1;b<t.participants.size();b++)
            result.add(new TournamentMatch(t.id+"-"+(result.size()+1),t.id,t.participants.get(a),t.participants.get(b)));
        return List.copyOf(result);
    }
}
