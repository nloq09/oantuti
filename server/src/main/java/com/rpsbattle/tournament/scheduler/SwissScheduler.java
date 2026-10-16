package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.ranking.*;
import com.rpsbattle.tournament.TournamentAudit;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

public final class SwissScheduler implements TournamentScheduler {
 public List<TournamentMatch> schedule(Tournament t){int limit=32-Integer.numberOfLeadingZeros(t.participants.size()-1);t.rounds.clear();t.bracket.clear();t.swiss=new SwissState(limit,0,false,null);return pair(t,1);}
 public static Map<String,Integer> points(Tournament t){var points=new HashMap<String,Integer>();for(var p:t.participants)points.put(p.participantId(),0);for(var round:t.rounds)for(var id:round.byeParticipantIds())points.merge(id,3,Integer::sum);
  for(var m:TournamentSeries.scored(t)){if(m.status!=TournamentMatch.Status.FINISHED)continue;int a="DOUBLE_FORFEIT".equals(m.decisionReason)?0:m.winnerParticipantId==null?1:m.participantA.participantId().equals(m.winnerParticipantId)?3:0;int b="DOUBLE_FORFEIT".equals(m.decisionReason)?0:m.winnerParticipantId==null?1:3-a;points.merge(m.participantA.participantId(),a,Integer::sum);points.merge(m.participantB.participantId(),b,Integer::sum);}return points;
 }
 public static int buchholz(Tournament t,String id){var points=points(t);return TournamentSeries.scored(t).stream().filter(m->m.status==TournamentMatch.Status.FINISHED).filter(m->id.equals(m.participantA.participantId())||id.equals(m.participantB.participantId())).mapToInt(m->points.getOrDefault(id.equals(m.participantA.participantId())?m.participantB.participantId():m.participantA.participantId(),0)).sum();}
 private static String key(String a,String b){return a.compareTo(b)<0?a+"/"+b:b+"/"+a;}
 private static List<TournamentMatch> pair(Tournament t,int round){
  var players=new ArrayList<>(new RankingService(new ClassicScoringRule()).rank(t).stream().filter(r->t.operations.active(r.participantId())).map(r->t.participants.stream().filter(p->p.participantId().equals(r.participantId())).findFirst().orElseThrow()).toList());
  if(round==1){players.clear();t.participants.stream().filter(p->t.operations.active(p.participantId())).forEach(players::add);}
  var played=new HashSet<String>();TournamentSeries.roots(t).forEach(m->played.add(key(m.participantA.participantId(),m.participantB.participantId())));
  var byes=new HashSet<String>();t.rounds.forEach(r->byes.addAll(r.byeParticipantIds()));var points=points(t);
  // Try the lowest ranked eligible BYE first, without forcing an otherwise impossible matching.
  var choices=new ArrayList<Integer>();if(players.size()%2==0)choices.add(-1);else for(int i=players.size()-1;i>=0;i--)if(!byes.contains(players.get(i).participantId()))choices.add(i);
  for(int bye:choices){var pool=new ArrayList<>(players);var resting=bye<0?null:pool.remove(bye);var graph=new ArrayList<List<Integer>>();for(int i=0;i<pool.size();i++){var neighbors=new ArrayList<Integer>();for(int j=0;j<pool.size();j++)if(i!=j&&!played.contains(key(pool.get(i).participantId(),pool.get(j).participantId())))neighbors.add(j);final int index=i;neighbors.sort(Comparator.comparingInt((Integer j)->Math.abs(points.getOrDefault(pool.get(index).participantId(),0)-points.getOrDefault(pool.get(j).participantId(),0))).thenComparingInt(j->j));graph.add(neighbors);}
   int[] matching=new SwissMatching(graph).solve();if(Arrays.stream(matching).anyMatch(i->i<0))continue;
   var result=new ArrayList<TournamentMatch>();for(int i=0;i<matching.length;i++)if(i<matching[i]){var m=new TournamentMatch(UUID.nameUUIDFromBytes((t.id+"/swiss/"+round+"/"+i).getBytes(StandardCharsets.UTF_8)).toString(),t.id,pool.get(i),pool.get(matching[i]));m.round=round;m.scheduledAt=Instant.now();result.add(m);}
   t.rounds.add(new TournamentRound(round,resting==null?List.of():List.of(resting.participantId())));t.swiss=new SwissState(t.swiss.totalRounds(),round,false,null);return List.copyOf(result);
  }
  return null;
 }
 public static boolean advance(Tournament t){if(t.format!=TournamentFormat.SWISS||t.swiss==null||t.swiss.complete()||t.status!=TournamentStatus.RUNNING||!t.operations.reviews().isEmpty()||t.matches.stream().anyMatch(m->m.status!=TournamentMatch.Status.FINISHED)||TournamentSeries.roots(t).stream().anyMatch(m->TournamentSeries.decision(t,m).isEmpty()))return false;
  String reason=t.swiss.currentRound()>=t.swiss.totalRounds()?"ROUND_LIMIT":null;List<TournamentMatch> next=null;
  if(reason==null){if(t.participants.stream().filter(p->t.operations.active(p.participantId())).count()<2)reason="INSUFFICIENT_ACTIVE_PLAYERS";else {next=pair(t,t.swiss.currentRound()+1);if(next==null)reason="NO_FRESH_PAIRING";}}
  if(reason!=null)t.swiss=new SwissState(t.swiss.totalRounds(),t.swiss.currentRound(),true,reason);else t.matches.addAll(next);
  TournamentAudit.append(t,reason==null?"SWISS_ROUND_PAIRED":"SWISS_COMPLETED",null,t.id,reason,null,Integer.toString(t.swiss.currentRound()));return true;
 }
 public static List<Map<String,Object>> standings(Tournament t,List<RankingEntry> ranks){if(t.swiss==null)return List.of();return ranks.stream().map(r->{var row=new LinkedHashMap<String,Object>();row.put("participantId",r.participantId());row.put("displayName",r.displayName());row.put("rank",r.rank());row.put("points",r.points());row.put("wins",r.wins());row.put("draws",r.draws());row.put("losses",r.losses());row.put("buchholz",buchholz(t,r.participantId()));row.put("byes",t.rounds.stream().filter(round->round.byeParticipantIds().contains(r.participantId())).map(TournamentRound::number).toList());return (Map<String,Object>)row;}).toList();}
}
