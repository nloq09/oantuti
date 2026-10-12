package com.rpsbattle.spectator;
import org.springframework.web.socket.WebSocketSession;
public record MatchSubscription(WebSocketSession session,String matchId,String tournamentId) {}
