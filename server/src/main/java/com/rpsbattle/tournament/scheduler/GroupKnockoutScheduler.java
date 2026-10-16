package com.rpsbattle.tournament.scheduler;

import com.rpsbattle.tournament.TournamentAudit;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.online.DemoRequestException;
import java.util.*;

/** Two seeded round-robin groups followed by A1-B2 / B1-A2 and the final. */
public final class GroupKnockoutScheduler implements TournamentScheduler {
    public List<TournamentMatch> schedule(Tournament t) {
        if(t.participants.size()<4||t.participants.size()>32)throw new DemoRequestException("GROUP_STAGE_REQUIRES_FOUR_PARTICIPANTS");
        var groups=new LinkedHashMap<String,List<String>>();groups.put("A",new ArrayList<>());groups.put("B",new ArrayList<>());
        for(int i=0;i<t.participants.size();i++)groups.get(i%4==0||i%4==3?"A":"B").add(t.participants.get(i).participantId());
        t.rounds.clear();t.bracket.clear();var result=new ArrayList<TournamentMatch>();var byes=new TreeMap<Integer,List<String>>();
        for(var entry:groups.entrySet()) {
            var group=subset(t,entry.getValue(),t.id+"/group/"+entry.getKey());
            for(var m:new RoundRobinScheduler().schedule(group)){var game=new TournamentMatch(m.id,t.id,m.participantA,m.participantB);game.round=m.round;game.scheduledAt=m.scheduledAt;result.add(game);}
            for(var round:group.rounds)byes.computeIfAbsent(round.number(),key->new ArrayList<>()).addAll(round.byeParticipantIds());
        }
        byes.forEach((round,ids)->t.rounds.add(new TournamentRound(round,ids)));
        t.groupStage=new GroupStageState(groups,t.rounds.size(),false,List.of());
        t.stages.clear();t.stages.add(new TournamentStage(t.id+"/stage/1",1,TournamentFormat.ROUND_ROBIN,2));
        t.stages.add(new TournamentStage(t.id+"/stage/2",2,TournamentFormat.SINGLE_ELIMINATION,null));
        return List.copyOf(result);
    }
    private static Tournament subset(Tournament t,List<String> ids,String id) {
        var group=new Tournament(id,t.name,TournamentFormat.ROUND_ROBIN,t.createdAt);
        group.bestOf=t.bestOf;group.tieBreakOrder=t.tieBreakOrder;group.operations=t.operations;
        for(var p:t.participants)if(ids.contains(p.participantId()))group.participants.add(p);
        if(t.groupStage!=null)group.matches.addAll(t.matches.stream().filter(m->m.round<=t.groupStage.rounds()&&ids.contains(m.participantA.participantId())&&ids.contains(m.participantB.participantId())).toList());
        return group;
    }
    public static List<RankingEntry> standings(Tournament t,String group) {
        return new RankingService(new ClassicScoringRule()).rank(subset(t,t.groupStage.groups().get(group),t.id));
    }
    /** Call under the tournament monitor. Commit the snapshot and bracket before worker admission. */
    public static boolean qualify(Tournament t) {
        if(t.format!=TournamentFormat.GROUP_KNOCKOUT||t.groupStage==null||t.groupStage.locked()||t.status!=TournamentStatus.RUNNING||!t.operations.reviews().isEmpty())return false;
        if(t.matches.isEmpty()||t.matches.stream().anyMatch(m->m.status!=TournamentMatch.Status.FINISHED)||TournamentSeries.roots(t).stream().anyMatch(m->TournamentSeries.decision(t,m).isEmpty()))return false;
        var a=standings(t,"A").stream().filter(r->t.operations.active(r.participantId())).limit(2).map(RankingEntry::participantId).toList();
        var b=standings(t,"B").stream().filter(r->t.operations.active(r.participantId())).limit(2).map(RankingEntry::participantId).toList();
        var seeds=new ArrayList<String>();for(int i=0;i<2;i++){seeds.add(a.size()>i?a.get(i):null);seeds.add(b.size()>i?b.get(i):null);}
        t.groupStage=new GroupStageState(t.groupStage.groups(),t.groupStage.rounds(),true,seeds);
        var players=new ArrayList<TournamentParticipant>();for(var seed:seeds)players.add(t.participants.stream().filter(p->p.participantId().equals(seed)).findFirst().orElse(null));
        t.matches.addAll(new SingleEliminationScheduler().schedule(t,players,t.groupStage.rounds(),t.id+"/stage/2"));
        TournamentAudit.append(t,"GROUP_QUALIFICATION_LOCKED",null,t.id,"TOP_TWO_PER_GROUP",null,String.join(",",seeds.stream().filter(Objects::nonNull).toList()));
        return true;
    }
    public static boolean hasKnockout(Tournament t){return t.format==TournamentFormat.SINGLE_ELIMINATION||t.format==TournamentFormat.GROUP_KNOCKOUT&&t.groupStage!=null&&t.groupStage.locked();}
    public static boolean complete(Tournament t){return t.format==TournamentFormat.ROUND_ROBIN||hasKnockout(t)&&SingleEliminationScheduler.complete(t);}
    public static int stage(Tournament t,int round){return t.groupStage!=null&&round>t.groupStage.rounds()?2:1;}
    public static String group(Tournament t,TournamentMatch m){return t.groupStage==null||stage(t,m.round)==2?null:t.groupStage.groups().entrySet().stream().filter(e->e.getValue().contains(m.participantA.participantId())).map(Map.Entry::getKey).findFirst().orElse(null);}
    public static List<Map<String,Object>> view(Tournament t){
        if(t.groupStage==null)return List.of();var rows=new ArrayList<Map<String,Object>>();
        t.groupStage.groups().forEach((name,ids)->{var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("participantIds",ids);row.put("ranking",standings(t,name));row.put("qualificationLocked",t.groupStage.locked());row.put("qualified",t.groupStage.qualifiers().stream().filter(Objects::nonNull).filter(ids::contains).toList());rows.add(row);});return rows;
    }
}
