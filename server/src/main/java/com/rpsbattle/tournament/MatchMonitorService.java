package com.rpsbattle.tournament;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.ownership.BotRepository;
import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Authorized presentation snapshot; never changes scheduling or match decisions. */
@Service
public class MatchMonitorService {
 private final TournamentService tournaments;private final TournamentAuthorizationService authorization;private final AccountAuthorizationService accounts;private final RemoteExecutionReadService remote;private final BotRepository bots;
 public MatchMonitorService(TournamentService tournaments,TournamentAuthorizationService authorization,AccountAuthorizationService accounts,RemoteExecutionReadService remote,BotRepository bots){this.tournaments=tournaments;this.authorization=authorization;this.accounts=accounts;this.remote=remote;this.bots=bots;}
 public static final Set<String> FILTERS=Set.of("ALL","QUEUED","ASSIGNED","STARTING","RUNNING","RESULT_PENDING","RETRYING","ERROR","FINISHED","FAILED","REVIEW","RECOVERING","CANCELLED");
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> page(Authentication auth,String id,int page,int size,String filter,boolean mine){
  var actor=accounts.requireUser(auth);var t=tournaments.find(id);if(!mine)authorization.requireManager(auth,t);
  if(page<0||page>10000||size<1||size>50||!FILTERS.contains(filter))throw new AuthException("INVALID_PAGING",400);
  synchronized(t){
   var selected=t.matches.stream().filter(m->!mine||actor.id().value().equals(m.participantA.ownerUserId())||actor.id().value().equals(m.participantB.ownerUserId())).toList();
   var execution=remote.forMatches(id,selected.stream().map(m->m.id).collect(java.util.stream.Collectors.toSet()));
   var rows=new ArrayList<Map<String,Object>>();var counts=new TreeMap<String,Long>();
   for(var m:selected){var e=execution.get(m.id);String state=e!=null?e.executionState():switch(m.status){case FINISHED->"FINISHED";case FAILED->"ERROR";case RUNNING->t.status==TournamentStatus.CANCELLED?"CANCELLED":"RUNNING_LOCAL";default->t.status==TournamentStatus.CANCELLED?"CANCELLED":"WAITING_OPPONENT";};counts.merge(state,1L,Long::sum);
    boolean include=switch(filter){case "ALL"->true;case "RUNNING"->Set.of("RUNNING_LOCAL","RUNNING_REMOTE").contains(state);case "FAILED"->m.status==TournamentMatch.Status.FAILED;case "REVIEW"->t.operations.reviews().containsKey(m.id);default->state.equals(filter);};if(!include)continue;
    var row=new LinkedHashMap<String,Object>();row.put("id",m.id);row.put("round",m.round);row.put("gameNumber",m.gameNumber);row.put("bestOf",t.bestOf);row.put("status",m.status);row.put("executionState",state);row.put("execution",e);row.put("participantA",m.participantA);row.put("participantB",m.participantB);row.put("onlineMatchId",e==null?m.onlineMatchId:null);row.put("historyMatchId",m.historyMatchId);row.put("winnerParticipantId",m.winnerParticipantId);row.put("decisionReason",m.decisionReason);row.put("failure",m.failure==null?null:"MATCH_FAILURE");row.put("scheduledAt",m.scheduledAt);row.put("startedAt",m.startedAt);row.put("finishedAt",m.finishedAt);row.put("attempt",e==null?m.attempt:e.attempt());row.put("reviewRequired",t.operations.reviews().containsKey(m.id));rows.add(row);
   }
   // Current/queued games first; completed games remain available on later pages.
   rows.sort(Comparator.comparingInt((Map<String,Object> r)->Set.of("FINISHED","ERROR","CANCELLED").contains(r.get("executionState"))?1:0).thenComparingInt(r->Set.of("FINISHED","ERROR","CANCELLED").contains(r.get("executionState"))?-(int)r.get("round"):(int)r.get("round")).thenComparing(r->(String)r.get("id")));
   int from=Math.min(rows.size(),page*size),to=Math.min(rows.size(),from+size);var result=new LinkedHashMap<String,Object>();result.put("tournamentId",id);result.put("revision",t.revision);result.put("serverNow",Instant.now());result.put("page",page);result.put("size",size);result.put("total",rows.size());result.put("counts",counts);var visible=rows.subList(from,to);for(var r:visible){r.put("participantA",side(t,(TournamentParticipant)r.get("participantA")));r.put("participantB",side(t,(TournamentParticipant)r.get("participantB")));}result.put("items",List.copyOf(visible));result.put("queuePositionNote",mine?"Thứ tự các job đang chờ trong các trận của bạn, không phải hàng đợi toàn giải hoặc ETA.":"Thứ tự tạo job đang chờ trong giải, không phải hàng đợi toàn hệ thống hoặc ETA.");return result;
  }
 }
 private Map<String,Object> side(Tournament t,TournamentParticipant p){
  var row=new LinkedHashMap<String,Object>();row.put("participantId",p.participantId());row.put("controllerType",p.controllerType());row.put("playerName",tournaments.username(p.ownerUserId()));
  row.put("displayName",p.displayName());row.put("botName",p.botId()==null?null:botName(p.botId()));row.put("botVersionId",t.registrations.stream().filter(r->r.id().toString().equals(p.participantId())).map(TournamentRegistration::botVersionId).filter(Objects::nonNull).findFirst().orElse(null));row.put("artifactHash",p.artifactHash());return row;
 }
 private String botName(String id){try{return bots.find(UUID.fromString(id)).map(b->b.name()).orElse(null);}catch(IllegalArgumentException invalid){return null;}}
}
