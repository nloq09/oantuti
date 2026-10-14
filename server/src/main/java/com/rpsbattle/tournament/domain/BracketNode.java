package com.rpsbattle.tournament.domain;
/** Immutable bracket snapshot. Sources and winnerTo refer to node IDs. */
public record BracketNode(String id, int round, int position, String matchId,
        String slotA, String slotB, String sourceA, String sourceB, String winnerTo,
        String winnerParticipantId, String resolution) {
    public BracketNode resolved(String a,String b,String winner,String reason) {
        return new BracketNode(id,round,position,matchId,a,b,sourceA,sourceB,winnerTo,winner,reason);
    }
}
