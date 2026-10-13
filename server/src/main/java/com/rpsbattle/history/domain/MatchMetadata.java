package com.rpsbattle.history.domain;

import java.time.Instant;
import java.util.*;

/** Metadata only. Never contains a board, engine, credentials or artifact paths. */
public record MatchMetadata(String id, String roomId, String mode, String status,
        UUID player1UserId, UUID player2UserId, UUID winnerUserId, String winnerSeat,
        String outcome, int turnCount, Instant startedAt, Instant finishedAt,
        Long durationMs, String ruleSet, Instant createdAt, List<Participant> participants) {
    public MatchMetadata {participants=List.copyOf(participants);}
    public enum ControllerType { HUMAN, BUILTIN_BOT, UPLOADED_BOT }
    public record Participant(String seat, UUID userId, UUID botId, ControllerType controllerType) {}
    public boolean selfPlay() {return player1UserId!=null && player1UserId.equals(player2UserId);}
    public String resultFor(UUID user) {
        if(!status.equals("FINISHED")) return null;
        if(selfPlay()) return "SELF_PLAY";
        if("DRAW".equals(outcome)) return "DRAW";
        if(winnerSeat==null) return "UNKNOWN";
        return user.equals(winnerUserId) ? "WIN" : "LOSS";
    }
}
