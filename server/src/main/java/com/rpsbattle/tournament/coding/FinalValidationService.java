package com.rpsbattle.tournament.coding;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;

@Service
public class FinalValidationService {
 private final FinalValidationStore store;private final SubmissionStore submissions;private final TournamentRegistry registry;private final TournamentService tournaments;private final OwnedTournamentService owned;private final TournamentAuthorizationService authorization;private final AccountAuthorizationService accounts;private final OwnedBotService bots;private final UserRepository users;private final Clock clock;
 public FinalValidationService(FinalValidationStore s,SubmissionStore b,TournamentRegistry r,TournamentService t,OwnedTournamentService o,TournamentAuthorizationService a,AccountAuthorizationService c,OwnedBotService bots,UserRepository u,Clock k){store=s;submissions=b;registry=r;tournaments=t;owned=o;authorization=a;accounts=c;this.bots=bots;users=u;clock=k;}
 private boolean mutable(Tournament t){return t.timeline!=null&&t.startedAt==null&&Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN,TournamentStatus.READY,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status);}
 @Scheduled(fixedDelay=1000,initialDelayString="${rps.final-validation.initial-delay-ms:2000}") public void tick(){process();}
 public void process(){process(null);}
 public void process(String only){int remaining=50;for(var t:registry.findAll())synchronized(t){if(only!=null&&!t.id.equals(only)||!mutable(t)||clock.instant().isBefore(t.timeline.validationStartAt()))continue;
  try{
   for(var user:store.expiredEmergencies(t.id,Math.min(10,remaining))){owned.submissionMutation(t,null,()->{var cutoff=store.emergencyCutoff(t.id,user);boolean active=users.findById(new UserId(user)).map(a->a.status()==AccountStatus.ACTIVE).orElse(false);var result=submissions.freezeAtDeadline(t,user,active,clock.instant(),cutoff,true);var r=result.registration();if(r!=null){t.registrations.removeIf(old->old.id().equals(r.id()));t.registrations.add(r);}TournamentAudit.append(t,"EMERGENCY_SUBMISSION_FROZEN",null,user.toString(),null,null,result.freeze().decision());tournaments.changed(t);});if(--remaining==0)return;}
   for(var freeze:store.pendingFreezes(t.id,Math.min(20,remaining))){owned.submissionMutation(t,null,()->{store.create(t,freeze);TournamentAudit.append(t,"FINAL_VALIDATION_QUEUED",null,freeze.toString(),null,null,null);tournaments.changed(t);});if(--remaining==0)return;}
   for(var id:store.unapplied(t.id,Math.min(20,remaining))){owned.submissionMutation(t,null,()->{if(store.fallback(t,id)){TournamentAudit.append(t,"FINAL_VALIDATION_FALLBACK",null,id.toString(),null,null,null);tournaments.changed(t);return;}var applied=store.apply(t,id);if(applied==null)return;
    for(int i=0;i<t.registrations.size();i++){var r=t.registrations.get(i);if(!r.id().equals(applied.registrationId()))continue;t.participants.removeIf(p->p.participantId().equals(r.id().toString()));if(applied.passed()){var execution=bots.registrationExecution(r.botId().toString());var p=new TournamentParticipant(r.id().toString(),r.botId().toString(),execution.token(),r.displayName(),UUID.randomUUID().getMostSignificantBits(),r.userId()).withHash(r.artifactHash());t.participants.add(p);t.registrations.set(i,r.withStatus(TournamentRegistration.Status.APPROVED,clock.instant()));}else if(applied.status().equals("FAILED"))t.registrations.set(i,r.withStatus(TournamentRegistration.Status.REJECTED,clock.instant()));}
    TournamentAudit.append(t,"TIMELINE_BOT_VALIDATION_"+applied.status(),null,store.detail(t.id,id).userId().toString(),null,null,applied.status());
    TournamentAudit.append(t,"FINAL_VALIDATION_"+applied.status(),null,id.toString(),null,null,applied.status());tournaments.changed(t);
   });if(--remaining==0)return;}
  }catch(RuntimeException retry){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=final_validation_retry tournamentId={} cause={}",t.id,retry.getClass().getSimpleName());}
 }}
 public Object dashboard(Authentication auth,String id,int page){var t=tournaments.find(id);authorization.requireManager(auth,t);if(page<0||page>10000)throw new AuthException("INVALID_PAGING",400);synchronized(t){return Map.of("items",store.page(id,page),"page",page,"size",25,"revision",t.revision);}}
 public record Action(Long revision,String reason,Instant deadline){}
 public void action(Authentication auth,String id,UUID validation,String action,Action request){var actor=accounts.requireUser(auth).id().value();var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){if(request.revision()==null||request.revision()!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);if(!mutable(t))throw new AuthException("TOURNAMENT_LOCKED",409);String reason=com.rpsbattle.admin.AdminDirectoryService.reason(request.reason());if(reason.length()>400)throw new AuthException("REASON_REQUIRED",400);owned.submissionMutation(t,auth,()->{var before=store.detail(id,validation);if(action.equals("retry"))store.retry(t,validation);else if(action.equals("emergency")){if(t.status!=TournamentStatus.REGISTRATION)throw new AuthException("EMERGENCY_NOT_ALLOWED",409);store.grantEmergency(t,validation,actor,reason,request.deadline());}else throw new AuthException("INVALID_ACTION",400);TournamentAudit.append(t,action.equals("retry")?"FINAL_VALIDATION_RETRY":"EMERGENCY_SUBMISSION_OPENED",actor,validation.toString(),reason,action.equals("retry")?before.status():t.timeline.codingCloseAt().toString(),action.equals("retry")?"QUEUED":request.deadline().toString());tournaments.changed(t);});}}
}
