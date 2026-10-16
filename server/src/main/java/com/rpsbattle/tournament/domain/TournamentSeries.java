package com.rpsbattle.tournament.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Series orchestrates ordinary matches; game facts and replay identities remain immutable. */
public final class TournamentSeries {
    private TournamentSeries() {}
    public static int validate(Integer bestOf) {
        int value=bestOf==null?1:bestOf;
        if(value!=1&&value!=3&&value!=5)throw new com.rpsbattle.online.DemoRequestException("INVALID_BEST_OF");
        return value;
    }
    public static List<TournamentMatch> roots(Tournament t) {return t.matches.stream().filter(m->m.id.equals(m.seriesId)).toList();}
    public static List<TournamentMatch> games(Tournament t,String root) {return t.matches.stream().filter(m->root.equals(m.seriesId)).sorted(Comparator.comparingInt(m->m.gameNumber)).toList();}
    public static Optional<TournamentMatch> decision(Tournament t,TournamentMatch root) {
        if(root==null)return Optional.empty();
        if(t.bestOf==1)return root.status==TournamentMatch.Status.FINISHED?Optional.of(root):Optional.empty();
        var games=games(t,root.id);
        if(games.isEmpty()||games.stream().anyMatch(m->t.operations.reviews().containsKey(m.id)||m.status!=TournamentMatch.Status.FINISHED))return Optional.empty();
        int a=0,b=0;TournamentMatch forfeit=null;
        for(var game:games){if(root.participantA.participantId().equals(game.winnerParticipantId))a++;else if(root.participantB.participantId().equals(game.winnerParticipantId))b++;if(Set.of("FORFEIT","DOUBLE_FORFEIT").contains(String.valueOf(game.decisionReason)))forfeit=game;}
        if(forfeit==null&&a<t.bestOf/2+1&&b<t.bestOf/2+1&&games.size()<t.bestOf)return Optional.empty();
        var result=root.copy();result.status=TournamentMatch.Status.FINISHED;
        result.winnerParticipantId=forfeit!=null?forfeit.winnerParticipantId:a==b?null:a>b?root.participantA.participantId():root.participantB.participantId();
        result.result=result.winnerParticipantId==null?"DRAW":"WIN";result.decisionReason=forfeit==null?"SERIES_SCORE":forfeit.decisionReason;
        result.remainingA=games.stream().mapToInt(m->m.remainingA).sum();result.remainingB=games.stream().mapToInt(m->m.remainingB).sum();
        result.moveCountA=games.stream().mapToInt(m->m.moveCountA).sum();result.moveCountB=games.stream().mapToInt(m->m.moveCountB).sum();
        result.moveNanosA=games.stream().mapToLong(m->m.moveNanosA).sum();result.moveNanosB=games.stream().mapToLong(m->m.moveNanosB).sum();result.finishedAt=games.getLast().finishedAt;
        return Optional.of(result);
    }
    public static List<TournamentMatch> scored(Tournament t){return t.bestOf==1?List.copyOf(t.matches):roots(t).stream().map(m->decision(t,m)).flatMap(Optional::stream).toList();}
    /** Caller holds the monitor and commits the new game before admitting it to workers. */
    public static boolean advance(Tournament t) {
        if(t.bestOf==1||t.status!=TournamentStatus.RUNNING)return false;boolean changed=false;
        for(var root:roots(t)) {
            var games=games(t,root.id);if(games.isEmpty()||decision(t,root).isPresent()||games.size()>=t.bestOf||games.stream().anyMatch(m->m.status!=TournamentMatch.Status.FINISHED||t.operations.reviews().containsKey(m.id)))continue;
            int number=games.getLast().gameNumber+1;String id=UUID.nameUUIDFromBytes((root.id+"/game/"+number).getBytes(StandardCharsets.UTF_8)).toString();
            if(t.matches.stream().anyMatch(m->m.id.equals(id)))continue;
            var next=new TournamentMatch(id,t.id,root.participantA,root.participantB);next.seriesId=root.id;next.gameNumber=number;next.round=root.round;next.scheduledAt=Instant.now();t.matches.add(next);
            com.rpsbattle.tournament.TournamentAudit.append(t,"SERIES_UPDATED",null,root.id,"NEXT_GAME",Integer.toString(number-1),Integer.toString(number));changed=true;
        }return changed;
    }
    public static Map<String,Object> view(Tournament t,TournamentMatch root){
        var games=games(t,root.id);var result=decision(t,root);var row=new LinkedHashMap<String,Object>();row.put("id",root.id);row.put("round",root.round);row.put("bestOf",t.bestOf);row.put("currentGame",games.stream().mapToInt(m->m.gameNumber).max().orElse(1));
        row.put("participantA",root.participantA);row.put("participantB",root.participantB);row.put("winsA",games.stream().filter(m->m.status==TournamentMatch.Status.FINISHED&&root.participantA.participantId().equals(m.winnerParticipantId)).count());row.put("winsB",games.stream().filter(m->m.status==TournamentMatch.Status.FINISHED&&root.participantB.participantId().equals(m.winnerParticipantId)).count());
        row.put("status",result.isPresent()?"FINISHED":games.stream().anyMatch(m->m.status==TournamentMatch.Status.FAILED)?"FAILED":games.stream().anyMatch(m->m.status==TournamentMatch.Status.RUNNING)?"RUNNING":"PENDING");row.put("winnerParticipantId",result.map(m->m.winnerParticipantId).orElse(null));row.put("gameIds",games.stream().map(m->m.id).toList());return row;
    }
}
