package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Seeded knockout tree, with explicit BYEs and no placeholder game rooms. */
public final class SingleEliminationScheduler implements TournamentScheduler {
    private static String id(Tournament t,int round,int position) {
        return UUID.nameUUIDFromBytes((t.id+"/bracket/"+round+"/"+position).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public List<TournamentMatch> schedule(Tournament t) {
        int size=2;while(size<t.participants.size())size*=2;
        List<Integer> seeds=new ArrayList<>(List.of(1,2));
        for(int n=4;n<=size;n*=2) {var next=new ArrayList<Integer>();for(int seed:seeds){next.add(seed);next.add(n+1-seed);}seeds=next;}
        int rounds=Integer.numberOfTrailingZeros(size);
        t.rounds.clear();t.bracket.clear();
        for(int round=1,count=size/2;round<=rounds;round++,count/=2) {
            var byes=new ArrayList<String>();
            for(int position=0;position<count;position++) {
                String a=null,b=null;
                if(round==1) {int sa=seeds.get(position*2),sb=seeds.get(position*2+1);a=sa<=t.participants.size()?t.participants.get(sa-1).participantId():null;b=sb<=t.participants.size()?t.participants.get(sb-1).participantId():null;}
                boolean bye=round==1&&(a==null||b==null);String winner=bye?(a==null?b:a):null;
                if(bye)byes.add(winner);
                t.bracket.add(new BracketNode(id(t,round,position),round,position,bye?null:id(t,round,position),a,b,
                    round==1?null:id(t,round-1,position*2),round==1?null:id(t,round-1,position*2+1),
                    round==rounds?null:id(t,round+1,position/2),winner,bye?"BYE":null));
            }
            t.rounds.add(new TournamentRound(round,byes));
        }
        return advance(t);
    }
    /** Idempotent; callers append returned games while holding the tournament monitor. */
    public static List<TournamentMatch> advance(Tournament t) {
        var created=new ArrayList<TournamentMatch>();
        var nodes=new HashMap<String,BracketNode>();for(var n:t.bracket)nodes.put(n.id(),n);
        var games=new HashMap<String,TournamentMatch>();for(var m:t.matches)games.put(m.id,m);
        var players=new HashMap<String,TournamentParticipant>();for(var p:t.participants)players.put(p.participantId(),p);
        for(int i=0;i<t.bracket.size();i++) {
            var n=t.bracket.get(i);String a=n.slotA(),b=n.slotB(),winner=n.winnerParticipantId(),reason=n.resolution();
            if(n.sourceA()!=null)a=nodes.get(n.sourceA()).winnerParticipantId();
            if(n.sourceB()!=null)b=nodes.get(n.sourceB()).winnerParticipantId();
            var game=games.get(n.matchId());
            if(winner==null && game!=null && game.status==TournamentMatch.Status.FINISHED && !t.operations.reviews().containsKey(game.id)) {
                winner=game.winnerParticipantId;reason="WIN";
                if("DOUBLE_FORFEIT".equals(game.decisionReason))reason="DOUBLE_FORFEIT";
                else if(winner==null) {
                    if(game.remainingA!=game.remainingB){winner=game.remainingA>game.remainingB?a:b;reason="DRAW_REMAINING_PIECES";}
                    else {var pa=players.get(a);var pb=players.get(b);int sa=pa.seedNumber()==null?t.participants.indexOf(pa)+1:pa.seedNumber();int sb=pb.seedNumber()==null?t.participants.indexOf(pb)+1:pb.seedNumber();winner=sa<sb?a:b;reason="DRAW_SEED";}
                }
            }
            if(n.sourceA()!=null && nodes.get(n.sourceA()).resolution()!=null && nodes.get(n.sourceB()).resolution()!=null && (a==null||b==null)) {winner=a==null?b:a;reason=winner==null?"NO_ELIGIBLE_PARTICIPANT":"WALKOVER";}
            if(n.matchId()!=null && game==null && a!=null && b!=null) {
                var match=new TournamentMatch(n.matchId(),t.id,players.get(a),players.get(b));match.round=n.round();match.scheduledAt=Instant.now();created.add(match);games.put(match.id,match);
            }
            var updated=n.resolved(a,b,winner,reason);t.bracket.set(i,updated);nodes.put(n.id(),updated);
        }
        return List.copyOf(created);
    }
    public static boolean complete(Tournament t) {return !t.bracket.isEmpty()&&t.bracket.getLast().resolution()!=null;}
}
