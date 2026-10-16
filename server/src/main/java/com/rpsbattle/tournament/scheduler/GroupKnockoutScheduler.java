package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.TournamentAudit;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.*;
import java.util.*;
/** Configurable round-robin qualification stages followed by the existing knockout scheduler. */
public final class GroupKnockoutScheduler implements TournamentScheduler {
 public List<TournamentMatch> schedule(Tournament t){
  if(t.participants.size()<4||t.participants.size()>32)throw new com.rpsbattle.online.DemoRequestException("GROUP_STAGE_REQUIRES_FOUR_PARTICIPANTS");
  GroupRoundPlan.validate(t.groupPlans,t.participants.size());t.rounds.clear();t.bracket.clear();t.groupHistory.clear();t.stages.clear();
  for(int i=0;i<t.groupPlans.size();i++)t.stages.add(new TournamentStage(t.id+"/stage/"+(i+1),i+1,TournamentFormat.ROUND_ROBIN,t.groupPlans.get(i).qualifyTopN()));
  t.stages.add(new TournamentStage(t.id+"/stage/"+(t.groupPlans.size()+1),t.groupPlans.size()+1,TournamentFormat.SINGLE_ELIMINATION,null));
  return scheduleGroups(t,t.participants,1,0);
 }
 private static List<TournamentMatch> scheduleGroups(Tournament t,List<TournamentParticipant> players,int stage,int offset){
  var plan=t.groupPlans.get(stage-1);var groups=new LinkedHashMap<String,List<String>>();for(int i=0;i<plan.groups();i++)groups.put(Character.toString('A'+i),new ArrayList<>());
  for(int i=0;i<players.size();i++){int block=i/plan.groups(),column=i%plan.groups();int group=block%2==0?column:plan.groups()-1-column;groups.get(Character.toString('A'+group)).add(players.get(i).participantId());}
  var result=new ArrayList<TournamentMatch>();var byes=new TreeMap<Integer,List<String>>();
  for(var entry:groups.entrySet()){var group=new Tournament(t.id+"/stage/"+stage+"/group/"+entry.getKey(),t.name,TournamentFormat.ROUND_ROBIN,t.createdAt);for(var p:players)if(entry.getValue().contains(p.participantId()))group.participants.add(p);
   for(var m:new RoundRobinScheduler().schedule(group)){var game=new TournamentMatch(m.id,t.id,m.participantA,m.participantB);game.round=offset+m.round;game.scheduledAt=m.scheduledAt;result.add(game);}
   for(var round:group.rounds)byes.computeIfAbsent(offset+round.number(),key->new ArrayList<>()).addAll(round.byeParticipantIds());
  }
  byes.forEach((round,ids)->t.rounds.add(new TournamentRound(round,ids)));int end=byes.isEmpty()?offset:byes.lastKey();t.groupStage=new GroupStageState(groups,end,false,List.of(),stage,offset);return List.copyOf(result);
 }
 private static Tournament subset(Tournament t,GroupStageState stage,List<String> ids){var group=new Tournament(t.id,t.name,TournamentFormat.ROUND_ROBIN,t.createdAt);group.bestOf=t.bestOf;group.tieBreakOrder=t.tieBreakOrder;group.operations=t.operations;for(var p:t.participants)if(ids.contains(p.participantId()))group.participants.add(p);group.matches.addAll(t.matches.stream().filter(m->m.round>stage.startRound()&&m.round<=stage.rounds()&&ids.contains(m.participantA.participantId())&&ids.contains(m.participantB.participantId())).toList());return group;}
 public static List<RankingEntry> standings(Tournament t,String group){return standings(t,t.groupStage,group);}
 public static List<RankingEntry> standings(Tournament t,GroupStageState stage,String group){return new RankingService(new ClassicScoringRule()).rank(subset(t,stage,stage.groups().get(group)));}
 public static boolean qualify(Tournament t){
  if(t.format!=TournamentFormat.GROUP_KNOCKOUT||t.groupStage==null||t.groupStage.locked()||t.status!=TournamentStatus.RUNNING||!t.operations.reviews().isEmpty())return false;
  if(t.matches.stream().anyMatch(m->m.status!=TournamentMatch.Status.FINISHED)||TournamentSeries.roots(t).stream().anyMatch(m->TournamentSeries.decision(t,m).isEmpty()))return false;
  var current=t.groupStage;int top=t.groupPlans.get(current.stageNumber()-1).qualifyTopN();var ranked=new ArrayList<List<String>>();for(var name:current.groups().keySet())ranked.add(standings(t,name).stream().filter(r->t.operations.active(r.participantId())).limit(top).map(RankingEntry::participantId).toList());
  var seeds=new ArrayList<String>();for(int i=0;i<top;i++)for(var group:ranked)seeds.add(i<group.size()?group.get(i):null);
  var frozen=new GroupStageState(current.groups(),current.rounds(),true,seeds,current.stageNumber(),current.startRound());t.groupStage=frozen;
  TournamentAudit.append(t,"GROUP_QUALIFICATION_LOCKED",null,t.id,"STAGE_"+current.stageNumber()+"_TOP_"+top,null,String.join(",",seeds.stream().filter(Objects::nonNull).toList()));
  if(current.stageNumber()<t.groupPlans.size()){
   t.groupHistory.add(frozen);var players=new ArrayList<TournamentParticipant>();for(var seed:seeds)t.participants.stream().filter(p->p.participantId().equals(seed)).findFirst().ifPresent(players::add);t.matches.addAll(scheduleGroups(t,players,current.stageNumber()+1,current.rounds()));
  }else{var players=new ArrayList<TournamentParticipant>();for(var seed:seeds)players.add(t.participants.stream().filter(p->p.participantId().equals(seed)).findFirst().orElse(null));t.matches.addAll(new SingleEliminationScheduler().schedule(t,players,current.rounds(),t.id+"/stage/"+(current.stageNumber()+1)));}
  return true;
 }
 public static boolean hasKnockout(Tournament t){return t.format==TournamentFormat.SINGLE_ELIMINATION||t.format==TournamentFormat.GROUP_KNOCKOUT&&t.groupStage!=null&&t.groupStage.locked();}
 public static boolean complete(Tournament t){return switch(t.format){case ROUND_ROBIN->true;case SWISS->t.swiss!=null&&t.swiss.complete();case TEAM_ROUND_ROBIN->TeamScheduler.complete(t);default->hasKnockout(t)&&SingleEliminationScheduler.complete(t);};}
 public static int stage(Tournament t,int round){for(var stage:t.groupHistory)if(round<=stage.rounds())return stage.stageNumber();return t.groupStage==null?1:round<=t.groupStage.rounds()?t.groupStage.stageNumber():t.groupStage.stageNumber()+1;}
 public static String group(Tournament t,TournamentMatch m){var stages=new ArrayList<>(t.groupHistory);if(t.groupStage!=null)stages.add(t.groupStage);return stages.stream().filter(s->m.round>s.startRound()&&m.round<=s.rounds()).flatMap(s->s.groups().entrySet().stream()).filter(e->e.getValue().contains(m.participantA.participantId())).map(Map.Entry::getKey).findFirst().orElse(null);}
 public static List<Map<String,Object>> view(Tournament t){var rows=new ArrayList<Map<String,Object>>();var stages=new ArrayList<>(t.groupHistory);if(t.groupStage!=null)stages.add(t.groupStage);for(var stage:stages)stage.groups().forEach((name,ids)->{var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("stageNumber",stage.stageNumber());row.put("qualifyTopN",t.groupPlans.get(stage.stageNumber()-1).qualifyTopN());row.put("participantIds",ids);row.put("ranking",standings(t,stage,name));row.put("qualificationLocked",stage.locked());row.put("qualified",stage.qualifiers().stream().filter(Objects::nonNull).filter(ids::contains).toList());rows.add(row);});return rows;}
}
