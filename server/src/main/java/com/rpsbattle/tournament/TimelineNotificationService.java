package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Durable, idempotent in-app reminders. Reads never emit events; obsolete reminders are not replayed. */
@Service public class TimelineNotificationService {
 private final TournamentRegistry registry;private final OwnedTournamentService owned;private final TournamentService tournaments;private final Clock clock;
 public TimelineNotificationService(TournamentRegistry r,OwnedTournamentService o,TournamentService t,Clock c){registry=r;owned=o;tournaments=t;clock=c;}
 public record Notice(String type,Instant at){}
 private static void window(List<Notice> out,String type,Instant trigger,Instant until,Instant now){if(!now.isBefore(trigger)&&now.isBefore(until))out.add(new Notice(type,trigger));}
 public static List<Notice> due(Tournament t,Instant now){
  var result=new ArrayList<Notice>();var s=t.timeline;
  if(s==null||!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY).contains(t.status))return result;
  window(result,"TIMELINE_REGISTRATION_OPENED",s.registrationOpenAt(),s.registrationCloseAt(),now);
  if(!s.registrationCloseAt().minusSeconds(3600).isBefore(s.registrationOpenAt()))window(result,"TIMELINE_REGISTRATION_CLOSING",s.registrationCloseAt().minusSeconds(3600),s.registrationCloseAt(),now);
  if(!s.codingOpenAt().minusSeconds(1800).isBefore(s.registrationCloseAt()))window(result,"TIMELINE_CODING_STARTING",s.codingOpenAt().minusSeconds(1800),s.codingOpenAt(),now);
  window(result,"TIMELINE_CODING_STARTED",s.codingOpenAt(),s.codingCloseAt(),now);
  int[] minutes={30,10,5,1};for(int i=0;i<minutes.length;i++){var trigger=s.codingCloseAt().minusSeconds(minutes[i]*60L);var until=i+1<minutes.length?s.codingCloseAt().minusSeconds(minutes[i+1]*60L):s.codingCloseAt();if(!trigger.isBefore(s.codingOpenAt()))window(result,"TIMELINE_CODING_"+minutes[i]+"_MIN_LEFT",trigger,until,now);}
  window(result,"TIMELINE_CODING_ENDED",s.codingCloseAt(),s.checkInCloseAt(),now);
  window(result,"TIMELINE_VALIDATION_STARTED",s.validationStartAt(),s.checkInOpenAt(),now);
  window(result,"TIMELINE_CHECK_IN_OPENED",s.checkInOpenAt(),s.checkInCloseAt(),now);
  if(!s.checkInCloseAt().minusSeconds(600).isBefore(s.checkInOpenAt()))window(result,"TIMELINE_CHECK_IN_CLOSING",s.checkInCloseAt().minusSeconds(600),s.checkInCloseAt(),now);
  if(!s.tournamentStartAt().minusSeconds(600).isBefore(s.checkInOpenAt()))window(result,"TIMELINE_TOURNAMENT_STARTING",s.tournamentStartAt().minusSeconds(600),s.tournamentStartAt(),now);
  return List.copyOf(result);
 }
 @Scheduled(fixedDelay=1000,initialDelayString="${rps.timeline-notifications.initial-delay-ms:2000}") public void tick(){process(null);}
 public void process(String only){int budget=25;for(var t:registry.findAll())synchronized(t){if(only!=null&&!only.equals(t.id))continue;var pending=due(t,clock.instant()).stream().filter(n->t.operations.audit().stream().noneMatch(a->a.type().equals(n.type())&&a.target().equals(n.at().toString()))).toList();if(pending.isEmpty())continue;
  try{owned.submissionMutation(t,null,()->{for(var n:pending)TournamentAudit.append(t,n.type(),null,n.at().toString(),null,null,null);tournaments.changed(t);});}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=timeline_notification_retry tournamentId={}",t.id);}
  if(--budget==0)return;
 }}
}
