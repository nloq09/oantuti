package com.rpsbattle.tournament.coding;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Durable per-user decisions; retries resume only unfinished users. No Docker jobs are launched here. */
@Service
public class CodingDeadlineService {
 private final com.rpsbattle.tournament.FairTournamentScan fairScan=new com.rpsbattle.tournament.FairTournamentScan();
 private final TournamentRegistry registry;private final SubmissionStore store;private final OwnedTournamentService owned;private final TournamentService tournaments;private final UserRepository users;private final Clock clock;
 public CodingDeadlineService(TournamentRegistry r,SubmissionStore s,OwnedTournamentService o,TournamentService t,UserRepository u,Clock c){registry=r;store=s;owned=o;tournaments=t;users=u;clock=c;}
 @Scheduled(fixedDelay=1000,initialDelayString="${rps.coding.deadline-initial-delay-ms:1000}") public void deadlines(){processDeadlines();}
 public void processDeadlines(){
  int remaining=50;
  for(var t:fairScan.next(registry.findAll()))synchronized(t){
   if(t.timeline==null||t.startedAt!=null||clock.instant().isBefore(t.timeline.codingCloseAt())||!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status))continue;
   var pending=store.pendingFreezeUsers(t.id,t.timeline.codingCloseAt(),Math.min(25,remaining));
   if(!pending.isEmpty())try{owned.submissionMutation(t,null,()->{for(var user:pending)freezeOne(t,user);tournaments.changed(t);});}
   catch(RuntimeException batchFailure){for(var user:pending)try{owned.submissionMutation(t,null,()->{freezeOne(t,user);tournaments.changed(t);});}catch(RuntimeException retry){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=coding_deadline_retry tournamentId={} userId={} cause={}",t.id,user,retry.getClass().getSimpleName());}}
   remaining-=pending.size();if(remaining<=0)return;
  }
 }

 private void freezeOne(Tournament t,java.util.UUID user){
  boolean active=users.findById(new UserId(user)).map(a->a.status()==AccountStatus.ACTIVE).orElse(false);
  var result=store.freezeAtDeadline(t,user,active,clock.instant());var registration=result.registration();
  if(registration!=null){t.registrations.removeIf(r->r.id().equals(registration.id()));t.registrations.add(registration);for(int i=0;i<t.participants.size();i++){var p=t.participants.get(i);if(p.participantId().equals(registration.id().toString()))t.participants.set(i,p.withHash(registration.artifactHash()));}}
  TournamentAudit.append(t,"CODING_DEADLINE_FROZEN",null,user.toString(),null,null,result.freeze().decision());
 }
}
