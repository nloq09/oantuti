package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.TournamentAudit;
import com.rpsbattle.online.DemoRequestException;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class TeamScheduler implements TournamentScheduler {
 public static void validateRoster(Tournament t){if(t.teams.teams().size()<2)throw new DemoRequestException("TEAM_ROSTER_INCOMPLETE");var used=new HashSet<String>();for(var team:t.teams.teams()){long count=team.roster().stream().filter(id->t.participants.stream().anyMatch(p->p.participantId().equals(id))&&t.operations.active(id)).count();if(count<3)throw new DemoRequestException("TEAM_ROSTER_INCOMPLETE");for(var id:team.roster())if(!used.add(id))throw new DemoRequestException("TEAM_MEMBER_DUPLICATE");}if(t.participants.stream().anyMatch(p->!used.contains(p.participantId())))throw new DemoRequestException("TEAM_ROSTER_INCOMPLETE");}
 public List<TournamentMatch> schedule(Tournament t){validateRoster(t);var schedule=new Tournament(t.id+"/teams",t.name,TournamentFormat.ROUND_ROBIN,t.createdAt);for(var team:t.teams.teams())schedule.participants.add(new TournamentParticipant(team.id(),null,"",team.name(),0));var fixtures=new ArrayList<TeamState.Fixture>();for(var m:new RoundRobinScheduler().schedule(schedule))fixtures.add(new TeamState.Fixture(m.id,m.round,m.participantA.participantId(),m.participantB.participantId(),List.of(),List.of(),false,List.of()));t.rounds.clear();t.rounds.addAll(schedule.rounds);t.bracket.clear();t.teams=new TeamState(t.teams.teams(),fixtures);return List.of();}
 public record Score(boolean complete,int a,int b,boolean doubleForfeit){}
 public static Score score(Tournament t,TeamState.Fixture fixture){if(!fixture.locked())return new Score(false,0,0,false);int a=0,b=0;boolean empty=true;for(var id:fixture.seriesIds()){var root=t.matches.stream().filter(m->m.id.equals(id)).findFirst().orElse(null);if(TournamentSeries.games(t,id).stream().anyMatch(m->t.operations.reviews().containsKey(m.id)))return new Score(false,a,b,false);var result=TournamentSeries.decision(t,root);if(result.isEmpty())return new Score(false,a,b,false);var game=result.get();if("DOUBLE_FORFEIT".equals(game.decisionReason))continue;empty=false;if(game.winnerParticipantId==null){a++;b++;}else if(game.participantA.participantId().equals(game.winnerParticipantId))a+=2;else b+=2;}return new Score(true,a,b,empty);}
 public static boolean complete(Tournament t){return !t.teams.fixtures().isEmpty()&&t.teams.fixtures().stream().allMatch(f->score(t,f).complete());}
 public static final int LINEUP_SECONDS=300;
 public static void resetLineupWindows(Tournament t){if(t.format!=TournamentFormat.TEAM_ROUND_ROBIN)return;t.teams=new TeamState(t.teams.teams(),t.teams.fixtures().stream().map(f->new TeamState.Fixture(f.id(),f.round(),f.teamA(),f.teamB(),f.lineupA(),f.lineupB(),f.locked(),f.seriesIds(),f.locked()?f.lineupDeadline():null)).toList());}
 public static boolean advance(Tournament t){return advance(t,Instant.now());}
 public static boolean advance(Tournament t,Instant now){
  if(t.format!=TournamentFormat.TEAM_ROUND_ROBIN||t.status!=TournamentStatus.RUNNING||!t.operations.reviews().isEmpty()||t.matches.stream().anyMatch(m->m.status==TournamentMatch.Status.FAILED))return false;
  int round=t.teams.fixtures().stream().filter(f->!score(t,f).complete()).mapToInt(TeamState.Fixture::round).min().orElse(Integer.MAX_VALUE);
  if(!t.operations.autoAdvanceRounds()&&round>t.operations.releasedRound())return false;
  boolean changed=false;var fixtures=new ArrayList<>(t.teams.fixtures());
  for(int i=0;i<fixtures.size();i++){
   var f=fixtures.get(i);if(f.round()!=round||f.locked())continue;
   var deadline=f.lineupDeadline();if(deadline==null){deadline=now.plusSeconds(LINEUP_SECONDS);f=new TeamState.Fixture(f.id(),f.round(),f.teamA(),f.teamB(),f.lineupA(),f.lineupB(),false,f.seriesIds(),deadline);fixtures.set(i,f);TournamentAudit.append(t,"TEAM_LINEUP_WINDOW_OPENED",null,f.id(),deadline.toString(),null,null);changed=true;}
   var a=f.lineupA();var b=f.lineupB();
   if(!now.isBefore(deadline)){
    if(a.size()!=3){a=fallback(t,f.teamA());TournamentAudit.append(t,"TEAM_LINEUP_AUTO_SELECTED",null,f.id(),f.teamA(),null,String.join(",",a));}
    if(b.size()!=3){b=fallback(t,f.teamB());TournamentAudit.append(t,"TEAM_LINEUP_AUTO_SELECTED",null,f.id(),f.teamB(),null,String.join(",",b));}
   }
   if(a.size()!=3||b.size()!=3)continue;
   var ids=new ArrayList<String>();for(int slot=0;slot<3;slot++){
    String id=UUID.nameUUIDFromBytes((f.id()+"/slot/"+slot).getBytes(StandardCharsets.UTF_8)).toString();ids.add(id);String paId=a.get(slot),pbId=b.get(slot);
    var pa=t.participants.stream().filter(p->p.participantId().equals(paId)).findFirst().orElseThrow();var pb=t.participants.stream().filter(p->p.participantId().equals(pbId)).findFirst().orElseThrow();var m=new TournamentMatch(id,t.id,pa,pb);m.round=f.round();m.scheduledAt=now;t.matches.add(m);
   }
   fixtures.set(i,new TeamState.Fixture(f.id(),f.round(),f.teamA(),f.teamB(),a,b,true,ids,deadline));TournamentAudit.append(t,"TEAM_LINEUPS_LOCKED",null,f.id(),null,null,null);changed=true;
  }
  if(changed)t.teams=new TeamState(t.teams.teams(),fixtures);return changed;
 }
 private static List<String> fallback(Tournament t,String id){return t.teams.teams().stream().filter(team->team.id().equals(id)).findFirst().orElseThrow().roster().stream().filter(member->t.participants.stream().anyMatch(p->p.participantId().equals(member))).sorted(Comparator.comparing(member->!t.operations.active(member))).limit(3).toList();}
 public static List<Map<String,Object>> standings(Tournament t){var rows=new ArrayList<Map<String,Object>>();for(var team:t.teams.teams()){int played=0,wins=0,draws=0,losses=0,duels=0,against=0;for(var f:t.teams.fixtures())if(team.id().equals(f.teamA())||team.id().equals(f.teamB())){var s=score(t,f);if(!s.complete())continue;played++;int own=team.id().equals(f.teamA())?s.a():s.b(),other=team.id().equals(f.teamA())?s.b():s.a();duels+=own;against+=other;if(s.doubleForfeit())losses++;else if(own==other)draws++;else if(own>other)wins++;else losses++;}var row=new LinkedHashMap<String,Object>();row.put("id",team.id());row.put("name",team.name());row.put("school",team.school());row.put("played",played);row.put("wins",wins);row.put("draws",draws);row.put("losses",losses);row.put("points",wins*3+draws);row.put("boardPoints",duels);row.put("boardDifference",duels-against);rows.add(row);}
  rows.sort(Comparator.comparing((Map<String,Object> r)->!eligible(t,r.get("id").toString())).thenComparing(Comparator.comparingInt((Map<String,Object> r)->(int)r.get("points")).reversed()).thenComparing(Comparator.comparingInt((Map<String,Object> r)->(int)r.get("boardDifference")).reversed()).thenComparing(r->r.get("id").toString()));for(int i=0;i<rows.size();i++)rows.get(i).put("rank",i+1);return rows;
 }
 private static boolean eligible(Tournament t,String id){return t.teams.teams().stream().anyMatch(team->team.id().equals(id)&&team.roster().stream().anyMatch(t.operations::active));}
 public static String winner(Tournament t){var ranking=standings(t);return t.status==TournamentStatus.FINISHED&&t.operations.reviews().isEmpty()&&!ranking.isEmpty()&&eligible(t,ranking.getFirst().get("id").toString())&&((int)ranking.getFirst().get("wins")>0||(int)ranking.getFirst().get("draws")>0)?ranking.getFirst().get("id").toString():null;}
 public static Map<String,List<String>> privateLineups(Tournament t,UUID actor){var result=new LinkedHashMap<String,List<String>>();for(var team:t.teams.teams())if(actor.equals(team.captain()))for(var f:t.teams.fixtures())if(team.id().equals(f.teamA())||team.id().equals(f.teamB()))result.put(f.id()+"/"+team.id(),team.id().equals(f.teamA())?f.lineupA():f.lineupB());return result;}
 public static Map<String,Object> view(Tournament t){var view=new LinkedHashMap<String,Object>();view.put("teams",t.teams.teams());view.put("lineupSeconds",LINEUP_SECONDS);view.put("lineupFallback","ROSTER_ORDER_ACTIVE_FIRST");view.put("ranking",standings(t));view.put("fixtures",t.teams.fixtures().stream().map(f->{var row=new LinkedHashMap<String,Object>();row.put("id",f.id());row.put("round",f.round());row.put("teamA",f.teamA());row.put("teamB",f.teamB());row.put("lineupA",f.locked()?f.lineupA():List.of());row.put("lineupB",f.locked()?f.lineupB():List.of());row.put("submittedA",!f.lineupA().isEmpty());row.put("submittedB",!f.lineupB().isEmpty());row.put("locked",f.locked());row.put("lineupDeadline",f.lineupDeadline());row.put("seriesIds",f.seriesIds());row.put("score",score(t,f));return row;}).toList());var ranking=standings(t);view.put("champion",t.status==TournamentStatus.FINISHED&&t.operations.reviews().isEmpty()&&!ranking.isEmpty()&&eligible(t,ranking.getFirst().get("id").toString())&&((int)ranking.getFirst().get("wins")>0||(int)ranking.getFirst().get("draws")>0)?ranking.getFirst():null);return view;}
}
