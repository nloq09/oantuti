package com.rpsbattle.tournament;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class TournamentMatchPageService {
 private final TournamentService tournaments;private final AccountAuthorizationService accounts;
 public TournamentMatchPageService(TournamentService tournaments,AccountAuthorizationService accounts){this.tournaments=tournaments;this.accounts=accounts;}
 public Map<String,Object> bracket(String id){var t=tournaments.find(id);synchronized(t){var selected=new ArrayList<TournamentMatch>();for(var node:t.bracket)if(node.matchId()!=null){var games=TournamentSeries.games(t,node.matchId());if(!games.isEmpty())selected.add(games.getLast());}return tournaments.view(t,selected,true);}}
 public Map<String,Object> fixture(String id,String fixtureId){var t=tournaments.find(id);synchronized(t){var f=t.teams.fixtures().stream().filter(row->row.id().equals(fixtureId)).findFirst().orElseThrow(()->new AuthException("TEAM_FIXTURE_NOT_FOUND",404));return tournaments.view(t,t.matches.stream().filter(m->f.seriesIds().contains(m.seriesId)).toList(),true);}}
 public Map<String,Object> page(Authentication auth,String id,int page,int size,Integer round,Integer stage,String status,boolean mine,String order,boolean replay){
  if(page<0||page>100000||size<1||size>50||round!=null&&round<1||stage!=null&&stage<1||!Set.of("round","time").contains(order)||status!=null&&!Set.of("PENDING","RUNNING","FINISHED","FAILED").contains(status))throw new AuthException("INVALID_PAGING",400);
  UUID actor=mine?accounts.requireUser(auth).id().value():null;var t=tournaments.find(id);synchronized(t){
   var sorted=t.matches.stream().filter(m->round==null||m.round==round).filter(m->stage==null||com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.stage(t,m.round)==stage).filter(m->status==null||m.status.name().equals(status)).filter(m->!replay||m.historyMatchId!=null).filter(m->!mine||actor.equals(m.participantA.ownerUserId())||actor.equals(m.participantB.ownerUserId())).sorted(order.equals("time")?Comparator.comparing((TournamentMatch m)->m.scheduledAt,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(m->m.id):Comparator.comparingInt((TournamentMatch m)->m.round).thenComparing(m->m.id)).toList();
   var selected=sorted.stream().skip((long)page*size).limit(size).toList();var view=tournaments.view(t,selected,true);var result=new LinkedHashMap<String,Object>();result.put("items",view.get("matches"));result.put("series",view.get("series"));result.put("page",page);result.put("size",size);result.put("total",sorted.size());result.put("revision",t.revision);return result;
  }
 }
}
