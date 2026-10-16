package com.rpsbattle.tournament;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;

/** Personal projection of durable audit facts; reading never mutates a match. */
@Service
public class TournamentNotificationService {
 private final AccountAuthorizationService accounts;private final TournamentService tournaments;private final OwnedTournamentService owned;private final TournamentRegistry registry;
 public TournamentNotificationService(AccountAuthorizationService accounts,TournamentService tournaments,OwnedTournamentService owned,TournamentRegistry registry){this.accounts=accounts;this.tournaments=tournaments;this.owned=owned;this.registry=registry;}
 private static final Set<String> GENERAL=Set.of("SWISS_ROUND_PAIRED","SWISS_COMPLETED","TEAM_CREATED","TEAM_REMOVED","TEAM_MEMBER_JOINED","TEAM_MEMBER_LEFT","TEAM_LINEUP_SUBMITTED","TEAM_LINEUPS_LOCKED","GROUP_QUALIFICATION_LOCKED","CHECK_IN_OPENED","TOURNAMENT_STARTED","TOURNAMENT_PAUSED","TOURNAMENT_RESUMED","ROUND_RELEASED","TOURNAMENT_FINISHED","TOURNAMENT_CANCELLED","TOURNAMENT_RECOVERY_REQUIRED");
 private static final Set<String> PERSONAL=Set.of("PARTICIPANT_APPROVED","REGISTRATION_UPDATED","CHECK_IN","PARTICIPANT_WITHDRAWN","PARTICIPANT_DISQUALIFIED");
 private static final Set<String> MATCH=Set.of("SERIES_UPDATED","MATCH_READY_REQUIRED","MATCH_STARTED","MATCH_FINISHED","MATCH_NO_SHOW","MATCH_REVIEW_REQUESTED","MATCH_REVIEW_ASSIGNED","MATCH_REVIEW_CLOSED","MATCH_REVIEW_REJECTED","RESULT_OVERRIDDEN","MATCH_RETRIED");
 private boolean belongs(Tournament t,UUID user){return user.equals(t.ownerUserId)||t.registrations.stream().anyMatch(r->r.userId().equals(user));}
 private List<Map<String,Object>> project(Tournament t,UUID user){
  var ids=new HashSet<String>();t.registrations.stream().filter(r->r.userId().equals(user)).forEach(r->ids.add(r.id().toString()));t.participants.stream().filter(p->user.equals(p.ownerUserId())).forEach(p->ids.add(p.participantId()));
  var games=new HashSet<String>();t.matches.stream().filter(m->ids.contains(m.participantA.participantId())||ids.contains(m.participantB.participantId())).forEach(m->games.add(m.id));
  long read=t.notificationReads.getOrDefault(user.toString(),0L);var result=new ArrayList<Map<String,Object>>();
  for(var e:t.operations.audit())if(GENERAL.contains(e.type())||PERSONAL.contains(e.type())&&ids.contains(e.target())||MATCH.contains(e.type())&&games.contains(e.target())){
   var row=new LinkedHashMap<String,Object>();row.put("id",e.id());row.put("sequence",e.sequence());row.put("type",e.type());row.put("tournamentId",t.id);row.put("tournamentName",t.name);row.put("createdAt",e.at());row.put("target",e.target());row.put("state",e.afterValue()!=null&&Set.of("APPROVED","REJECTED","CANCELLED","CHECKED_IN","NOT_CHECKED_IN").contains(e.afterValue())?e.afterValue():null);row.put("read",e.sequence()<=read);result.add(row);
  }return result;
 }
 public Map<String,Object> list(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!belongs(t,user))throw new AuthException("TOURNAMENT_FORBIDDEN",403);var rows=project(t,user);return Map.of("items",rows.stream().sorted(Comparator.comparingLong(row->-((Number)row.get("sequence")).longValue())).limit(50).toList(),"unread",rows.stream().filter(row->!Boolean.TRUE.equals(row.get("read"))).count(),"through",t.operations.audit().size());}}
 public void read(Authentication auth,String id,long through){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!belongs(t,user))throw new AuthException("TOURNAMENT_FORBIDDEN",403);if(through<0||through>t.operations.audit().size())throw new AuthException("INVALID_NOTIFICATION_CURSOR",400);long effective=project(t,user).stream().mapToLong(row->((Number)row.get("sequence")).longValue()).filter(sequence->sequence<=through).max().orElse(0);if(effective<=t.notificationReads.getOrDefault(user.toString(),0L))return;owned.systemMutation(t,()->{t.notificationReads.put(user.toString(),effective);TournamentAudit.append(t,"NOTIFICATIONS_READ",user,t.id,null,null,Long.toString(effective));tournaments.changed(t);});}}
 public Map<String,Object> feed(Authentication auth){var user=accounts.requireUser(auth).id().value();var rows=new ArrayList<Map<String,Object>>();var active=new ArrayList<Map<String,Object>>();var cursors=new LinkedHashMap<String,Long>();
  for(var t:registry.findAll())synchronized(t){if(!belongs(t,user))continue;rows.addAll(project(t,user));cursors.put(t.id,(long)t.operations.audit().size());if(Set.of(TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status)&&t.registrations.stream().anyMatch(r->r.userId().equals(user)&&Set.of(TournamentRegistration.Status.APPROVED,TournamentRegistration.Status.CHECKED_IN).contains(r.status())))active.add(Map.of("id",t.id,"name",t.name,"status",t.status));}
  return Map.of("items",rows.stream().sorted(Comparator.comparing(row->(java.time.Instant)row.get("createdAt"),Comparator.reverseOrder())).limit(50).toList(),"unread",rows.stream().filter(row->!Boolean.TRUE.equals(row.get("read"))).count(),"through",cursors,"active",active.stream().sorted(Comparator.comparing(row->row.get("name").toString())).limit(3).toList());
 }
}
