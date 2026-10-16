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
    private final Set<String> compactSubscriptions=ConcurrentHashMap.newKeySet();
    private final Map<String,Long> auditCursors=new ConcurrentHashMap<>();
    public SpectatorService(OnlineMatchService online,ReplayRegistry replays,TournamentService tournaments,ObjectMapper json) {
        this.online=online;this.replays=replays;this.tournaments=tournaments;this.json=json;
        online.listen(update->{for(var s:List.copyOf(subscriptions.values()))if(update.match().id().equals(s.matchId()))
            state(s,online.snapshot(update.match(),OnlineMatch.P1,update.events()));});
        tournaments.listen(t->{for(var s:List.copyOf(subscriptions.values()))if(t.id.equals(s.tournamentId())){
            send(s.session(),"TOURNAMENT_STATE",Map.of("tournament",compactSubscriptions.contains(s.session().getId())?Map.of("id",t.id,"revision",t.revision):tournaments.view(t)));
            send(s.session(),"TOURNAMENT_UPDATED",Map.of("tournamentId",t.id,"revision",t.revision));
            long cursor=auditCursors.getOrDefault(s.session().getId(),0L);
            for(var e:t.operations.audit())if(e.sequence()>cursor){String type=switch(e.type()){case "PARTICIPANT_APPROVED","CHECK_IN","CHECK_IN_OPENED","REGISTRATION_UPDATED"->"REGISTRATION_UPDATED";case "ROUND_STARTED","MATCH_STARTED","MATCH_FINISHED","TOURNAMENT_FINISHED"->e.type();default->"TOURNAMENT_UPDATED";};
                send(s.session(),type,Map.of("tournamentId",t.id,"revision",t.revision,"event",e));
                if(e.type().equals("MATCH_FINISHED")||e.type().equals("RESULT_OVERRIDDEN")){send(s.session(),"STANDINGS_UPDATED",Map.of("revision",t.revision));send(s.session(),"BRACKET_UPDATED",Map.of("revision",t.revision));}
            }
            if(subscriptions.containsKey(s.session().getId()))auditCursors.put(s.session().getId(),(long)t.operations.audit().size());
        }});
    }
    public boolean owns(WebSocketSession session){return subscriptions.containsKey(session.getId());}
    public void closed(WebSocketSession session){compactSubscriptions.remove(session.getId());subscriptions.remove(session.getId());auditCursors.remove(session.getId());}
    /** Live turn counts update without changing tournament revision or taking room->tournament locks. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=1000)
    public void refreshLiveTournaments(){for(var id:subscriptions.values().stream().map(MatchSubscription::tournamentId).filter(Objects::nonNull).distinct().toList())try{var t=tournaments.find(id);synchronized(t){if(t.status==com.rpsbattle.tournament.domain.TournamentStatus.RUNNING||t.status==com.rpsbattle.tournament.domain.TournamentStatus.PAUSED)tournaments.notifyChanged(t);}}catch(RuntimeException ignored){}}
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
                synchronized(t){if(message.path("updatesOnly").asBoolean(false))compactSubscriptions.add(session.getId());else compactSubscriptions.remove(session.getId());subscriptions.put(session.getId(),s);auditCursors.put(session.getId(),(long)t.operations.audit().size());send(s.session(),"TOURNAMENT_STATE",Map.of("tournament",compactSubscriptions.contains(s.session().getId())?Map.of("id",t.id,"revision",t.revision):tournaments.view(t)));}
            } else send(session,"ACTION_REJECTED",Map.of("error","SPECTATOR_READ_ONLY"));
        }catch(DemoRequestException error){send(session,"ACTION_REJECTED",Map.of("error",error.code()));}
        catch(com.rpsbattle.auth.AuthException error){send(session,"ACTION_REJECTED",Map.of("error",error.code()));}
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
