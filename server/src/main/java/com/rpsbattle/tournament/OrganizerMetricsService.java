package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class OrganizerMetricsService {
 private final TournamentService tournaments;private final TournamentAuthorizationService authorization;private final OrganizerMetricsStore store;private final Clock clock;
 public OrganizerMetricsService(TournamentService t,TournamentAuthorizationService a,OrganizerMetricsStore s,Clock c){tournaments=t;authorization=a;store=s;clock=c;}
 public record Coding(boolean available,long approved,long workspaceUsers,long submitted,long ready,long buildFailed,long notStarted){}
 public record CheckIn(long eligible,long checkedIn,long missing,long missed,long disqualified){}
 public record Running(int currentRound,long total,long completed,long running,long queued,long retrying,long error){}
 public record View(String tournamentId,long revision,Instant serverNow,TournamentPhase phase,Instant phaseEndsAt,Map<String,Long> registration,Coding coding,Map<String,Long> validation,CheckIn checkIn,Running running,OrganizerMetricsStore.Workers workers){}
 public View read(Authentication auth,String id){var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){var now=clock.instant();return project(t,store.read(UUID.fromString(t.id),now),now);}}
 static View project(Tournament t,OrganizerMetricsStore.Snapshot data,Instant now){
  var registration=new LinkedHashMap<String,Long>();for(String key:List.of("registered","approved","pending","waitlisted","rejected","cancelled"))registration.put(key,0L);
  Collection<String> statuses=t.timeline!=null?data.applications().values():t.registrations.stream().map(r->Set.of(TournamentRegistration.Status.CHECKED_IN,TournamentRegistration.Status.NOT_CHECKED_IN).contains(r.status())?"APPROVED":r.status().name()).toList();registration.put("registered",(long)statuses.size());for(String value:statuses){String key=value.toLowerCase(Locale.ROOT);if(registration.containsKey(key))registration.merge(key,1L,Long::sum);}
  registration.put("needsReview",registration.get("pending"));
  var approved=data.applications().entrySet().stream().filter(e->e.getValue().equals("APPROVED")).map(Map.Entry::getKey).collect(Collectors.toSet());var active=new HashSet<UUID>();var ready=new HashSet<UUID>();var failed=new HashSet<UUID>();
  for(var w:data.workspaces())if(approved.contains(w.user())){active.add(w.user());if(Set.of("READY","SUBMITTED","LOCKED").contains(Objects.toString(w.versionState(),"")))ready.add(w.user());if("BUILD_FAILED".equals(w.versionState()))failed.add(w.user());}failed.removeAll(ready);
  long submitted=data.submissions().entrySet().stream().filter(e->approved.contains(e.getKey())&&Set.of("SUBMITTED","LOCKED").contains(e.getValue())).count();
  var coding=new Coding(t.timeline!=null,approved.size(),active.size(),submitted,ready.size(),failed.size(),Math.max(0,approved.size()-active.size()));
  var validation=new LinkedHashMap<String,Long>();for(String key:List.of("total","queued","running","passed","failed","infraError","emergencyOpen"))validation.put(key,0L);var passed=new HashSet<UUID>();
  for(var v:data.validations()){validation.merge("total",1L,Long::sum);boolean emergency=v.emergencyDeadline()!=null&&now.isBefore(v.emergencyDeadline());String key=emergency?"emergencyOpen":v.state().equals("INFRA_ERROR")?"infraError":v.state().toLowerCase(Locale.ROOT);if(validation.containsKey(key))validation.merge(key,1L,Long::sum);if(!emergency&&v.state().equals("PASSED")&&v.applied())passed.add(v.user());}
  long eligible=0,checked=0,missed=0;var disqualified=new HashSet<UUID>();for(var s:data.submissions().entrySet())if(s.getValue().equals("DISQUALIFIED"))disqualified.add(s.getKey());
  for(var p:t.participants)if(t.operations.participants().get(p.participantId())==ParticipantStatus.DISQUALIFIED)disqualified.add(p.ownerUserId());
  for(var r:t.registrations){boolean qualifies=Set.of(TournamentRegistration.Status.APPROVED,TournamentRegistration.Status.CHECKED_IN,TournamentRegistration.Status.NOT_CHECKED_IN).contains(r.status())&&!disqualified.contains(r.userId())&&(t.timeline==null||approved.contains(r.userId())&&passed.contains(r.userId()));if(qualifies){eligible++;if(r.status()==TournamentRegistration.Status.CHECKED_IN)checked++;if(r.status()==TournamentRegistration.Status.NOT_CHECKED_IN||t.timeline!=null&&r.status()!=TournamentRegistration.Status.CHECKED_IN&&!now.isBefore(t.timeline.checkInCloseAt()))missed++;}}
  var matches=t.matches;var running=new Running(matches.stream().filter(m->Set.of(TournamentMatch.Status.PENDING,TournamentMatch.Status.RUNNING).contains(m.status)).mapToInt(m->m.round).min().orElse(t.rounds.size()),matches.size(),matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED).count(),matches.stream().filter(m->m.status==TournamentMatch.Status.RUNNING).count(),matches.stream().filter(m->m.status==TournamentMatch.Status.PENDING).count(),matches.stream().filter(m->m.attempt>1&&Set.of(TournamentMatch.Status.PENDING,TournamentMatch.Status.RUNNING).contains(m.status)).count(),matches.stream().filter(m->m.status==TournamentMatch.Status.FAILED).count());
  boolean reviewed=registration.get("pending")==0&&t.registrations.stream().noneMatch(r->r.status()==TournamentRegistration.Status.PENDING);var phase=TournamentPhaseResolver.resolve(t.status,t.timeline,now,null,reviewed);
  return new View(t.id,t.revision,now,phase.phase(),phase.phaseEndsAt(),Map.copyOf(registration),coding,Map.copyOf(validation),new CheckIn(eligible,checked,Math.max(0,eligible-checked),missed,t.timeline!=null?disqualified.size():t.operations.participants().values().stream().filter(s->s==ParticipantStatus.DISQUALIFIED).count()),running,data.workers());
 }
}
