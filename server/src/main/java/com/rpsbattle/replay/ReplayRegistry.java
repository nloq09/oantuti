package com.rpsbattle.replay;
import com.rpsbattle.api.dto.OnlineSnapshot;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.online.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Retains the existing MatchLog model; web DTOs are projections, not a second replay engine. */
@Component
public class ReplayRegistry {
    private final Map<String,MatchLog> logs=new ConcurrentHashMap<>();
    private final Map<String,OnlineSnapshot> finals=new ConcurrentHashMap<>();
    private final GameStateMapper mapper;
    public ReplayRegistry(OnlineMatchService online,GameStateMapper mapper) {
        this.mapper=mapper;
        online.listen(update->{ if(update.match().state().isFinished()) save(update.match(),online); });
    }
    public void save(OnlineMatch room,OnlineMatchService online) {
        if(logs.containsKey(room.id()))return;
        var log=room.replayLog();
        var snapshot=online.snapshot(room,OnlineMatch.P1,List.of());
        // A bounded in-memory archive; oldest insertion is not required for demo retention.
        synchronized(logs) {
            if(logs.containsKey(room.id()))return;
            if(logs.size()>=1000) { var old=logs.keySet().iterator().next();logs.remove(old);finals.remove(old); }
            finals.put(room.id(),snapshot); logs.put(room.id(),log);
        }
    }
    public OnlineSnapshot finalSnapshot(String id) {
        var value=finals.get(id); if(value==null)throw new DemoRequestException("MATCH_NOT_FOUND");return value;
    }
    public Map<String,Object> view(String id) {
        var log=logs.get(id); if(log==null)throw new DemoRequestException("REPLAY_NOT_READY");
        return Map.of("matchId",id,"ruleSet",log.ruleSet(),"seed",log.seed(),
            "initialState",mapper.toDto(log.initialState(),List.of(),List.of()),
            "events",mapper.toDto(log.initialState(),List.of(),log.events()).events(),
            "result",Map.of("outcome",log.result().outcome().name(),"winner",log.result().winner().map(p->p.value()).orElse("DRAW")),
            "finalState",finalSnapshot(id).gameState(),"players",finalSnapshot(id).players());
    }
}
