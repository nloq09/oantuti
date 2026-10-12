package com.rpsbattle.spectator;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.api.dto.OnlineSnapshot;
import com.rpsbattle.online.*;
import com.rpsbattle.replay.ReplayRegistry;
import com.rpsbattle.tournament.TournamentService;
import com.rpsbattle.websocket.message.ServerMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class SpectatorService {
    private final OnlineMatchService online;
    private final ReplayRegistry replays;
    private final TournamentService tournaments;
    private final ObjectMapper json;
    private final Map<String,MatchSubscription> subscriptions=new ConcurrentHashMap<>();
    public SpectatorService(OnlineMatchService online,ReplayRegistry replays,TournamentService tournaments,ObjectMapper json) {
        this.online=online;this.replays=replays;this.tournaments=tournaments;this.json=json;
        online.listen(update->{for(var s:List.copyOf(subscriptions.values()))if(update.match().id().equals(s.matchId()))
            state(s,online.snapshot(update.match(),OnlineMatch.P1,update.events()));});
        tournaments.listen(t->{for(var s:List.copyOf(subscriptions.values()))if(t.id.equals(s.tournamentId()))send(s.session(),"TOURNAMENT_STATE",Map.of("tournament",tournaments.view(t)));});
    }
    public boolean owns(WebSocketSession session){return subscriptions.containsKey(session.getId());}
    public void closed(WebSocketSession session){subscriptions.remove(session.getId());}
    public OnlineSnapshot snapshot(String id) {
        try {var room=online.find(id);return online.snapshot(room,OnlineMatch.P1,room.events());}
        catch(DemoRequestException error){if(!error.code().equals("MATCH_NOT_FOUND"))throw error;return replays.finalSnapshot(id);}
    }
    public void handle(WebSocketSession session,JsonNode message) {
        try {
            var type=message.path("type").asText();
            if(type.equals("PING")){send(session,"PONG",Map.of());return;}
            if(type.equals("SPECTATE_MATCH")) {
                var id=message.path("matchId").asText(); var snapshot=snapshot(id);
                var s=new MatchSubscription(new ConcurrentWebSocketSessionDecorator(session,5000,1048576),id,null);
                subscriptions.put(session.getId(),s);state(s,snapshot);
            } else if(type.equals("WATCH_TOURNAMENT")) {
                var t=tournaments.find(message.path("tournamentId").asText());
                var s=new MatchSubscription(new ConcurrentWebSocketSessionDecorator(session,5000,1048576),null,t.id);
                subscriptions.put(session.getId(),s);send(s.session(),"TOURNAMENT_STATE",Map.of("tournament",tournaments.view(t)));
            } else send(session,"ACTION_REJECTED",Map.of("error","SPECTATOR_READ_ONLY"));
        }catch(DemoRequestException error){send(session,"ACTION_REJECTED",Map.of("error",error.code()));}
        catch(RuntimeException error){send(session,"ACTION_REJECTED",Map.of("error","INVALID_MESSAGE"));}
    }
    private void state(MatchSubscription s,OnlineSnapshot snapshot) {
        send(s.session(),"GAME_STATE",Map.of("snapshot",snapshot));
        if(snapshot.status().equals("FINISHED"))send(s.session(),"GAME_OVER",Map.of("snapshot",snapshot));
    }
    private void send(WebSocketSession session,String type,Map<String,Object> payload) {
        try {if(session.isOpen())session.sendMessage(new TextMessage(json.writeValueAsString(new ServerMessage(type,payload))));}
        catch(Exception error){closed(session);try{session.close(CloseStatus.SERVER_ERROR);}catch(Exception ignored){}}
    }
}
