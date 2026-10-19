package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Reuses the durable lifecycle and existing execution queue; never starts a parallel runner. */
@Service
public class TimelineStartService {
 private final com.rpsbattle.tournament.FairTournamentScan fairScan=new com.rpsbattle.tournament.FairTournamentScan();
 private final TournamentRegistry registry;private final OwnedTournamentService owned;private final TournamentService tournaments;private final Clock clock;
 public TimelineStartService(TournamentRegistry r,OwnedTournamentService o,TournamentService t,Clock c){registry=r;owned=o;tournaments=t;clock=c;}
 @Scheduled(fixedDelay=1000,initialDelayString="${rps.timeline-start.initial-delay-ms:2000}") public void tick(){process(null);}
 public void process(String only){int remaining=25;for(var t:fairScan.next(registry.findAll())){
  synchronized(t){if(only!=null&&!t.id.equals(only)||t.timeline==null||t.startedAt!=null||!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY).contains(t.status)||clock.instant().isBefore(t.timeline.tournamentStartAt()))continue;}
  try{owned.startTimeline(t,null);}catch(RuntimeException failure){
   String reason=Set.of("SERVER_BUSY","BOT_SANDBOX_UNAVAILABLE","ARTIFACT_CHANGED","BOT_ARTIFACT_CHANGED","FINAL_VALIDATION_REQUIRED","BOT_SUBMISSION_NOT_LOCKED","RULE_SNAPSHOT_MISMATCH","INVALID_GROUP_PLAN","INVALID_TEAM_ROSTER").contains(String.valueOf(failure.getMessage()))?failure.getMessage():"START_PREPARATION_FAILED";
   synchronized(t){if(t.startedAt==null&&Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY).contains(t.status))try{
    var last=t.operations.audit().stream().filter(a->a.type().equals("TIMELINE_START_BLOCKED")).reduce((a,b)->b).orElse(null);
    if(last==null||!reason.equals(last.reason()))owned.submissionMutation(t,null,()->{TournamentAudit.append(t,"TIMELINE_START_BLOCKED",null,t.id,reason,null,null);tournaments.changed(t);});
   }catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=timeline_start_retry tournamentId={}",t.id);}}
  }
  if(--remaining==0)return;
 }}
 public static String issue(Tournament t){if(t.timeline==null||!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.CANCELLED).contains(t.status))return null;return t.operations.audit().stream().filter(a->a.type().equals("TIMELINE_START_BLOCKED")||a.type().equals("TOURNAMENT_CANCELLED")).reduce((a,b)->b).map(TournamentAuditEvent::reason).orElse(null);}
}
