package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.TieBreak;
import java.time.Instant;
import java.util.*;

/** Versioned tournament-owned structure, committed atomically with participants and results. */
public record TournamentDetails(int version,int maxConcurrentMatches,List<TieBreak> tieBreakOrder,
        List<TournamentRound> rounds,List<BracketNode> bracket,Map<String,MatchDetails> matches,List<TournamentStage> stages,TournamentOperations operations,ParticipantPolicy participantPolicy) {
    public TournamentDetails {tieBreakOrder=List.copyOf(tieBreakOrder);rounds=List.copyOf(rounds);bracket=List.copyOf(bracket);matches=Map.copyOf(matches);stages=List.copyOf(stages);}
    public record MatchDetails(int round,Instant scheduledAt,long moveNanosA,long moveNanosB,int moveCountA,int moveCountB,int attempt,String decisionReason) {}
    public static TournamentDetails snapshot(Tournament t) {
        var games=new LinkedHashMap<String,MatchDetails>();
        for(var m:t.matches)games.put(m.id,new MatchDetails(m.round,m.scheduledAt,m.moveNanosA,m.moveNanosB,m.moveCountA,m.moveCountB,m.attempt,m.decisionReason));
        return new TournamentDetails(1,t.maxConcurrentMatches,t.tieBreakOrder,t.rounds,t.bracket,games,t.stages,t.operations,t.participantPolicy);
    }
    public void restore(Tournament t) {
        if(version!=1)throw new IllegalStateException("Unsupported tournament structure version");
        t.maxConcurrentMatches=maxConcurrentMatches;t.tieBreakOrder=tieBreakOrder;
        t.rounds.clear();t.rounds.addAll(rounds);t.bracket.clear();t.bracket.addAll(bracket);
        t.stages.clear();t.stages.addAll(stages);
        t.operations=operations==null?TournamentOperations.defaults():operations;
        t.participantPolicy=participantPolicy==null?ParticipantPolicy.BOT_ONLY:participantPolicy;
        for(var m:t.matches) {var d=matches.get(m.id);if(d==null)continue;m.round=d.round();m.scheduledAt=d.scheduledAt();m.moveNanosA=d.moveNanosA();m.moveNanosB=d.moveNanosB();m.moveCountA=d.moveCountA();m.moveCountB=d.moveCountB();m.attempt=Math.max(1,d.attempt());m.decisionReason=d.decisionReason();}
    }
}
