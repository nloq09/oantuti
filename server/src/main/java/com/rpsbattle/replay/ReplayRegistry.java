package com.rpsbattle.replay;
import com.rpsbattle.api.dto.OnlineSnapshot;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.history.MatchMetadataMapper;
import com.rpsbattle.online.*;
import org.springframework.stereotype.Component;
import java.util.*;
/** Public web projections use the same MatchLog and recorded-facts replay engine. */
@Component
public class ReplayRegistry {
    private final ReplayArchiveStore archives;
    private final GameStateMapper mapper;
    private final Map<OnlineMatch,Boolean> captured=Collections.synchronizedMap(new WeakHashMap<>());
    public ReplayRegistry(OnlineMatchService online,GameStateMapper mapper,ReplayArchiveStore archives) {
        this.mapper=mapper;this.archives=archives;
        online.listen(update->{if(update.match().state().isFinished()) save(update.match(),online);});
    }
    public void save(OnlineMatch room,OnlineMatchService online) {
        synchronized(room) {
            if(captured.containsKey(room)) return;
            archives.capture(MatchMetadataMapper.capture(room),room.replayLog(),online.snapshot(room,OnlineMatch.P1,List.of()));
            captured.put(room,true);
        }
    }
    public OnlineSnapshot finalSnapshot(String id) {return archives.load(id).snapshot();}
    public Map<String,Object> view(String id) {
        var archive=archives.load(id);var log=archive.log();
        var view=new LinkedHashMap<String,Object>();
        view.put("matchId",log.matchId());view.put("ruleSet",log.ruleSet());view.put("seed",log.seed());
        view.put("initialState",mapper.toDto(log.initialState(),List.of(),List.of()));
        view.put("events",mapper.toDto(log.initialState(),List.of(),log.events()).events());
        view.put("result",Map.of("outcome",log.result().outcome().name(),"winner",log.result().winner().map(p->p.value()).orElse("DRAW")));
        view.put("finalState",archive.snapshot().gameState());view.put("players",archive.snapshot().players());
        return view;
    }
}
