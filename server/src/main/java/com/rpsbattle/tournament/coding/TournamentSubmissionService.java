package com.rpsbattle.tournament.coding;
import com.rpsbattle.account.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class TournamentSubmissionService {
 @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.timeline.TimelineAccessGuard timelineAccess;
 private final AccountAuthorizationService accounts;private final TournamentService tournaments;private final OwnedTournamentService owned;private final TournamentAuthorizationService authorization;private final SubmissionStore store;private final UserRepository users;private final TournamentRegistry registry;
 public TournamentSubmissionService(AccountAuthorizationService a,TournamentService t,OwnedTournamentService o,TournamentAuthorizationService z,SubmissionStore s,UserRepository u,TournamentRegistry r){accounts=a;tournaments=t;owned=o;authorization=z;store=s;users=u;registry=r;}
 public SubmissionStore.Summary mine(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(t.timeline!=null)timelineAccess.readable(t,user);return store.mine(id,user);}}
 public List<Map<String,Object>> versions(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(t.timeline!=null)timelineAccess.readable(t,user);return store.versions(id,user);}}
 public void submit(Authentication auth,String id,UUID version,long revision,boolean accepted){submit(auth,id,version,revision,accepted,false);}
 private void submit(Authentication auth,String id,UUID version,long revision,boolean accepted,boolean auto){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){
  if(t.timeline!=null)timelineAccess.coding(t,user);Instant now=Instant.now();if(!accepted)throw new AuthException("RULES_ACCEPTANCE_REQUIRED",400);if(t.timeline==null&&(t.status!=TournamentStatus.REGISTRATION||t.botPolicy.submissionOpenAt()!=null&&now.isBefore(t.botPolicy.submissionOpenAt())||!auto&&t.botPolicy.submissionDeadline()!=null&&!now.isBefore(t.botPolicy.submissionDeadline())))throw new AuthException("WORKSPACE_CLOSED",409);if(revision!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
  owned.submissionMutation(t,auth,()->{store.enable(id);var old=store.mine(id,user);if(old==null&&t.registrations.stream().filter(TournamentRegistration::active).count()>=t.maxParticipants)throw new AuthException("PARTICIPANT_LIMIT",400);
   var registration=store.submit(t,user,version,auto);t.registrations.removeIf(r->r.id().equals(registration.id()));t.registrations.add(registration);
   // Approved roster retains its stable execution identity across resubmits.
   for(int i=0;i<t.participants.size();i++){var p=t.participants.get(i);if(p.participantId().equals(registration.id().toString()))t.participants.set(i,p.withHash(registration.artifactHash()));}
   TournamentAudit.append(t,auto?"BOT_AUTO_SUBMITTED":"BOT_SUBMITTED",user,registration.id().toString(),null,old==null?null:old.versionId().toString(),version.toString());tournaments.changed(t);
  });
 }}
 public Object dashboard(Authentication auth,String id,int page){var t=tournaments.find(id);authorization.requireManager(auth,t);if(page<0||page>10000)throw new AuthException("INVALID_PAGING",400);return Map.of("items",store.dashboard(id,page),"page",page,"size",25);}
 public void action(Authentication auth,String id,UUID submission,String action,String reason,long revision){var t=tournaments.find(id);authorization.requireManager(auth,t);synchronized(t){if(revision!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);if(!Set.of(TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status))throw new AuthException("TOURNAMENT_LOCKED",409);owned.submissionMutation(t,auth,()->{if(action.equals("retest"))store.retest(id,submission,reason,accounts.requireUser(auth).id().value());else store.action(id,submission,action,reason,accounts.requireUser(auth).id().value());TournamentAudit.append(t,"SUBMISSION_"+action.toUpperCase(Locale.ROOT),accounts.requireUser(auth).id().value(),submission.toString(),reason,null,null);tournaments.changed(t);});}}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=1000) public void deadlines(){for(var t:registry.findAll())synchronized(t){if(t.timeline!=null||t.status!=TournamentStatus.REGISTRATION||t.botPolicy.submissionDeadline()==null||Instant.now().isBefore(t.botPolicy.submissionDeadline())||store.deadlineDone(t.id))continue;
  if(t.botPolicy.deadlineBehavior()==TournamentBotPolicy.DeadlineBehavior.AUTO_SUBMIT_LATEST_READY)for(var user:store.readyUsers(t.id)){try{var existing=store.mine(t.id,user);if(existing!=null&&existing.status().equals("LOCKED"))continue;var account=users.findById(new UserId(user)).orElseThrow();if(account.status()!=AccountStatus.ACTIVE)continue;UUID ready=store.latestReady(t.id,user);if(ready!=null&&store.newerThanSubmission(ready,existing))submit(AuthService.sessionAuthentication(account),t.id,ready,t.revision,true,true);}catch(RuntimeException rejected){store.autoRejected(t.id,user);}}
  if(t.botPolicy.lockAtDeadline())store.lockAll(t.id);store.finishDeadline(t.id);
 }}
}
