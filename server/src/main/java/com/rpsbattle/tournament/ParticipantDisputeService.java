package com.rpsbattle.tournament;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

/** Participant reports feed the existing referee audit/review workflow, never result overrides. */
@Service
public class ParticipantDisputeService {
 private final AccountAuthorizationService accounts;private final TournamentService tournaments;private final OwnedTournamentService owned;private final Clock clock;private final int hours;
 public ParticipantDisputeService(AccountAuthorizationService accounts,TournamentService tournaments,OwnedTournamentService owned,Clock clock,@Value("${rps.tournament.dispute-hours:24}")int hours){if(hours<1||hours>168)throw new IllegalArgumentException("Invalid dispute window");this.accounts=accounts;this.tournaments=tournaments;this.owned=owned;this.clock=clock;this.hours=hours;}
 private boolean mine(TournamentMatch m,UUID user){return user.equals(m.participantA.ownerUserId())||user.equals(m.participantB.ownerUserId());}
 private boolean inWindow(Tournament t,TournamentMatch m){return t.status!=TournamentStatus.CANCELLED&&(m.status==TournamentMatch.Status.FINISHED||m.status==TournamentMatch.Status.FAILED)&&m.finishedAt!=null&&clock.instant().isBefore(m.finishedAt.plus(Duration.ofHours(hours)));}
 public Map<String,Object> list(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){var rows=new ArrayList<Map<String,Object>>();for(var m:t.matches)if(mine(m,user)&&(m.status==TournamentMatch.Status.FINISHED||m.status==TournamentMatch.Status.FAILED)){
  var requests=t.operations.audit().stream().filter(e->e.type().equals("MATCH_REVIEW_REQUESTED")&&m.id.equals(e.target())).toList();var latest=requests.isEmpty()?null:requests.getLast();String status="NONE";
  if(latest!=null){var last=t.operations.audit().stream().filter(e->m.id.equals(e.target())&&e.sequence()>latest.sequence()&&Set.of("MATCH_REVIEW_ASSIGNED","MATCH_REVIEW_REJECTED","MATCH_REVIEW_CLOSED","RESULT_OVERRIDDEN","MATCH_RETRIED").contains(e.type())).reduce((a,b)->b).orElse(null);status=t.operations.reviews().containsKey(m.id)?last!=null&&last.type().equals("MATCH_REVIEW_ASSIGNED")?"UNDER_REVIEW":"OPEN":last!=null&&last.type().equals("MATCH_REVIEW_REJECTED")?"REJECTED":"RESOLVED";}
  var row=new LinkedHashMap<String,Object>();row.put("matchId",m.id);row.put("seriesId",m.seriesId);row.put("gameNumber",m.gameNumber);row.put("round",m.round);row.put("name",m.participantA.displayName()+" vs "+m.participantB.displayName());row.put("status",status);row.put("deadline",m.finishedAt==null?null:m.finishedAt.plus(Duration.ofHours(hours)));row.put("canReport",inWindow(t,m)&&!t.operations.reviews().containsKey(m.id)&&requests.stream().noneMatch(e->user.equals(e.actor())));row.put("reason",latest==null?null:latest.reason());rows.add(row);
 }return Map.of("items",rows,"serverTime",clock.instant(),"windowHours",hours);}}
 public void report(Authentication auth,String id,String matchId,String reason,String description){var user=accounts.requireUser(auth).id().value();if(reason==null||!Set.of("NETWORK","BOT","RESULT","OTHER").contains(reason)||description==null||description.isBlank()||description.length()>400)throw new AuthException("INVALID_DISPUTE",400);var t=tournaments.find(id);synchronized(t){var m=t.matches.stream().filter(game->game.id.equals(matchId)).findFirst().orElseThrow(()->new AuthException("TOURNAMENT_MATCH_NOT_FOUND",404));if(!mine(m,user))throw new AuthException("TOURNAMENT_FORBIDDEN",403);if(!inWindow(t,m))throw new AuthException("DISPUTE_WINDOW_CLOSED",409);if(t.operations.reviews().containsKey(m.id)||t.operations.audit().stream().anyMatch(e->e.type().equals("MATCH_REVIEW_REQUESTED")&&m.id.equals(e.target())&&user.equals(e.actor())))throw new AuthException("DISPUTE_ALREADY_REPORTED",409);
  owned.systemMutation(t,()->{String why=reason+": "+description.trim();if(t.status==TournamentStatus.RUNNING){t.readyWindows.clear();TournamentStateMachine.transition(t,TournamentStatus.PAUSED,clock.instant());}t.operations=t.operations.review(m.id,why);TournamentAudit.append(t,"MATCH_REVIEW_REQUESTED",user,m.id,why,null,TournamentAudit.result(m));tournaments.changed(t);});
 }}
}
