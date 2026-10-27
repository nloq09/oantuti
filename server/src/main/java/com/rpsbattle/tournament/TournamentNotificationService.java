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
 @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.timeline.TimelineRegistrationStore applications;
 private static final Set<String> GENERAL=Set.of("CLEANUP_CANDIDATE","TOURNAMENT_RETAINED","TOURNAMENT_RESTORED","SWISS_ROUND_PAIRED","SWISS_COMPLETED","TEAM_CREATED","TEAM_REMOVED","TEAM_MEMBER_JOINED","TEAM_MEMBER_LEFT","TEAM_LINEUP_SUBMITTED","TEAM_LINEUPS_LOCKED","GROUP_QUALIFICATION_LOCKED","CHECK_IN_OPENED","TOURNAMENT_STARTED","TOURNAMENT_PAUSED","TOURNAMENT_RESUMED","ROUND_RELEASED","TOURNAMENT_FINISHED","TOURNAMENT_CANCELLED","TOURNAMENT_RECOVERY_REQUIRED");
 private static final Set<String> PERSONAL=Set.of("PARTICIPANT_APPLICATION_SUBMITTED","PARTICIPANT_APPLICATION_WITHDRAWN","WAITLIST_PROMOTED","WAITLIST_REJECTED","PARTICIPANT_APPLICATION_REVIEWED","TIMELINE_BOT_VALIDATION_PASSED","TIMELINE_BOT_VALIDATION_FAILED","TIMELINE_BOT_VALIDATION_INFRA_ERROR","PARTICIPANT_CHECKED_IN","CHECK_IN_MISSED","PARTICIPANT_APPROVED","REGISTRATION_UPDATED","CHECK_IN","PARTICIPANT_WITHDRAWN","PARTICIPANT_DISQUALIFIED");
 private static final Set<String> MATCH=Set.of("SERIES_UPDATED","MATCH_READY_REQUIRED","MATCH_STARTED","MATCH_FINISHED","MATCH_NO_SHOW","MATCH_REVIEW_REQUESTED","MATCH_REVIEW_ASSIGNED","MATCH_REVIEW_CLOSED","MATCH_REVIEW_REJECTED","RESULT_OVERRIDDEN","MATCH_RETRIED");
 private boolean belongs(Tournament t,UUID user){return user.equals(t.ownerUserId)||t.registrations.stream().anyMatch(r->r.userId().equals(user))||t.timeline!=null&&applications.mine(UUID.fromString(t.id),user)!=null;}
 private List<Map<String,Object>> project(Tournament t,UUID user){
  var person=t.timeline==null?null:applications.notificationMembership(UUID.fromString(t.id),user);
  return project(t,user,person);
 }
 private List<Map<String,Object>> project(Tournament t,UUID user,com.rpsbattle.tournament.timeline.TimelineRegistrationStore.NotificationMembership person){
  var ids=new HashSet<String>();ids.add(user.toString());if(person!=null)ids.add(person.id().toString());t.registrations.stream().filter(r->r.userId().equals(user)).forEach(r->ids.add(r.id().toString()));t.participants.stream().filter(p->user.equals(p.ownerUserId())).forEach(p->ids.add(p.participantId()));
  var games=new HashSet<String>();t.matches.stream().filter(m->ids.contains(m.participantA.participantId())||ids.contains(m.participantB.participantId())).forEach(m->games.add(m.id));
  long read=t.notificationReads.getOrDefault(user.toString(),0L);var result=new ArrayList<Map<String,Object>>();
  for(var e:t.operations.audit())if(timelineVisible(t,user,person,e.type())||GENERAL.contains(e.type())||PERSONAL.contains(e.type())&&(ids.contains(e.target())||e.type().equals("PARTICIPANT_APPLICATION_WITHDRAWN")&&user.equals(e.actor()))||MATCH.contains(e.type())&&games.contains(e.target())){
   var row=new LinkedHashMap<String,Object>();row.put("id",e.id());row.put("sequence",e.sequence());row.put("type",e.type());row.put("tournamentId",t.id);row.put("tournamentName",t.name);row.put("createdAt",e.at());row.put("target",e.target());row.put("state",e.afterValue()!=null&&Set.of("APPROVED","REJECTED","CANCELLED","CHECKED_IN","NOT_CHECKED_IN","WAITLISTED","NEEDS_REVIEW").contains(e.afterValue())?e.afterValue():null);row.put("read",e.sequence()<=read);result.add(row);
  }return result;
 }
 private boolean timelineVisible(Tournament t,UUID user,com.rpsbattle.tournament.timeline.TimelineRegistrationStore.NotificationMembership person,String type){if(!type.startsWith("TIMELINE_")||type.startsWith("TIMELINE_BOT_")||type.equals("TIMELINE_START_BLOCKED"))return false;if(user.equals(t.ownerUserId))return true;if(person==null||person.status().equals("CANCELLED")||person.status().equals("REJECTED"))return false;return type.startsWith("TIMELINE_REGISTRATION_")||person.status().equals("APPROVED");}
 public Map<String,Object> list(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!belongs(t,user))throw new AuthException("TOURNAMENT_FORBIDDEN",403);var rows=project(t,user);return Map.of("items",rows.stream().sorted(Comparator.comparingLong(row->-((Number)row.get("sequence")).longValue())).limit(50).toList(),"unread",rows.stream().filter(row->!Boolean.TRUE.equals(row.get("read"))).count(),"through",t.operations.audit().size());}}
 public void read(Authentication auth,String id,long through){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!belongs(t,user))throw new AuthException("TOURNAMENT_FORBIDDEN",403);if(through<0||through>t.operations.audit().size())throw new AuthException("INVALID_NOTIFICATION_CURSOR",400);long effective=project(t,user).stream().mapToLong(row->((Number)row.get("sequence")).longValue()).filter(sequence->sequence<=through).max().orElse(0);if(effective<=t.notificationReads.getOrDefault(user.toString(),0L))return;owned.systemMutation(t,()->{t.notificationReads.put(user.toString(),effective);TournamentAudit.append(t,"NOTIFICATIONS_READ",user,t.id,null,null,Long.toString(effective));tournaments.changed(t);});}}
 private record Projection(long revision,int auditSize,long read,com.rpsbattle.tournament.timeline.TimelineRegistrationStore.NotificationMembership person,List<Map<String,Object>> items,long unread) {}
 private record ProjectionKey(String tournamentId,UUID user) {}
 private final Map<ProjectionKey,Projection> projections=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<ProjectionKey,Projection> entry){return size()>512;}});
 private Projection cached(Tournament t,UUID user,com.rpsbattle.tournament.timeline.TimelineRegistrationStore.NotificationMembership person){
  var key=new ProjectionKey(t.id,user);
  long read=t.notificationReads.getOrDefault(user.toString(),0L);var before=projections.get(key);
  if(before!=null&&before.revision()==t.revision&&before.auditSize()==t.operations.audit().size()&&before.read()==read&&Objects.equals(before.person(),person))return before;
  var rows=project(t,user,person);var result=new Projection(t.revision,t.operations.audit().size(),read,person,rows.stream().sorted(Comparator.comparing(row->(java.time.Instant)row.get("createdAt"),Comparator.reverseOrder())).limit(50).map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList(),rows.stream().filter(row->!Boolean.TRUE.equals(row.get("read"))).count());projections.put(key,result);return result;
 }
 public Map<String,Object> feed(Authentication auth){var user=accounts.requireUser(auth).id().value();var rows=new ArrayList<Map<String,Object>>();var active=new ArrayList<Map<String,Object>>();var cursors=new LinkedHashMap<String,Long>();long unread=0;
  long readSequence=registry.changeSequence();
  var memberships=applications.notificationMemberships(user);
  var cups=new HashSet<>(registry.relatedTo(user));memberships.keySet().forEach(id->registry.findById(id.toString()).ifPresent(cups::add));
  for(var t:cups)synchronized(t){if(t.archived)continue;var person=memberships.get(UUID.fromString(t.id));
   // A membership changed after the batch read must be re-authorized under its tournament lock.
   if(t.timeline!=null&&registry.changeSequence()!=readSequence)person=applications.notificationMembership(UUID.fromString(t.id),user);
   if(!user.equals(t.ownerUserId)&&t.registrations.stream().noneMatch(r->r.userId().equals(user))&&(t.timeline==null||person==null))continue;
   var projection=cached(t,user,person);rows.addAll(projection.items());unread+=projection.unread();cursors.put(t.id,(long)t.operations.audit().size());if(Set.of(TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status)&&t.registrations.stream().anyMatch(r->r.userId().equals(user)&&Set.of(TournamentRegistration.Status.APPROVED,TournamentRegistration.Status.CHECKED_IN).contains(r.status())))active.add(Map.of("id",t.id,"name",t.name,"status",t.status));}
  return Map.of("items",rows.stream().sorted(Comparator.comparing(row->(java.time.Instant)row.get("createdAt"),Comparator.reverseOrder())).limit(50).toList(),"unread",unread,"through",cursors,"active",active.stream().sorted(Comparator.comparing(row->row.get("name").toString())).limit(3).toList());
 }
}
