package com.rpsbattle.tournament.domain;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record TournamentParticipant(String participantId, String botId, @JsonIgnore String botToken, String displayName, long seed, java.util.UUID ownerUserId) {
    public TournamentParticipant(String id,String bot,String token,String name,long seed) {this(id,bot,token,name,seed,null);}
    @Override public String toString() {return "TournamentParticipant[id="+participantId+", botId="+botId+", token=[redacted]]";}
}
