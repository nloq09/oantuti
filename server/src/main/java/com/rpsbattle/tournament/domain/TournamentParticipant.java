package com.rpsbattle.tournament.domain;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record TournamentParticipant(String participantId, String botId, @JsonIgnore String botToken, String displayName, long seed, java.util.UUID ownerUserId,String artifactHash,Integer seedNumber,Integer manualSeed) {
    public TournamentParticipant(String id,String bot,String token,String name,long seed,java.util.UUID owner){this(id,bot,token,name,seed,owner,null,null,null);}
    public TournamentParticipant(String id,String bot,String token,String name,long seed) {this(id,bot,token,name,seed,null);}
    public TournamentParticipant withHash(String hash){return new TournamentParticipant(participantId,botId,botToken,displayName,seed,ownerUserId,hash,seedNumber,manualSeed);}
    public TournamentParticipant withSeeding(Integer number,Integer manual){return new TournamentParticipant(participantId,botId,botToken,displayName,seed,ownerUserId,artifactHash,number,manual);}
    @Override public String toString() {return "TournamentParticipant[id="+participantId+", botId="+botId+", token=[redacted]]";}
}
