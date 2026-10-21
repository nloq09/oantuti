package com.rpsbattle.tournament.cleanup;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
@Service
public class TournamentCleanupService {
 private final TournamentRegistry registry;private final TournamentCleanupStore store;private final TournamentPersistenceCoordinator persistence;private final OwnedTournamentService owned;private final TournamentAuthorizationService authorization;private final AccountAuthorizationService accounts;private final Clock clock;private final TournamentService tournaments;
 private int cursor;
 @Value("${rps.tournament.cleanup.enabled:true}") private boolean enabled=true;
 public TournamentCleanupService(TournamentRegistry registry,TournamentCleanupStore store,TournamentPersistenceCoordinator persistence,OwnedTournamentService owned,TournamentAuthorizationService authorization,AccountAuthorizationService accounts,Clock clock,TournamentService tournaments){this.registry=registry;this.store=store;this.persistence=persistence;this.owned=owned;this.authorization=authorization;this.accounts=accounts;this.clock=clock;this.tournaments=tournaments;}
 @Scheduled(fixedDelayString="${rps.tournament.cleanup.interval-ms:3600000}",initialDelayString="${rps.tournament.cleanup.initial-delay-ms:60000}") public void tick(){if(enabled)process();}
 /** Two independent observations, stable revision, elapsed grace, then repeat DB reference checks. */
 public synchronized void process(){
  var batch=new ArrayList<>(registry.findAll());batch.sort(Comparator.comparing(t->t.id));if(batch.isEmpty())return;
  Collections.rotate(batch,-Math.floorMod(cursor,batch.size()));cursor=Math.floorMod(cursor+25,batch.size());
  for(var t:batch.stream().limit(25).toList())synchronized(t){
   try{
    var now=clock.instant();var id=UUID.fromString(t.id);var state=store.state(id);var reason=TournamentCleanupPolicy.reason(t,now);
    if(reason.isEmpty()||state!=null&&state.protectedUntil()!=null&&state.protectedUntil().isAfter(now)||store.hasReferences(id)){if(state!=null&&state.candidateAt()!=null)owned.submissionMutation(t,null,()->{TournamentAudit.append(t,"CLEANUP_CLEARED",null,t.id,"ACTIVITY_OR_PROTECTED_DATA",null,null);tournaments.changed(t);store.clear(id);});continue;}
    if(state==null||state.candidateAt()==null||state.revision()!=t.revision||!reason.get().equals(state.reason())){
     owned.submissionMutation(t,null,()->{
      t.operations=t.operations.append(new TournamentAuditEvent(UUID.randomUUID(),t.operations.audit().size()+1L,"CLEANUP_CANDIDATE",null,t.id,reason.get(),null,now.plus(TournamentCleanupPolicy.GRACE).toString(),now));tournaments.changed(t);persistence.persistNow(t);
      if(!store.mark(id,t.revision,now,reason.get()))throw new AuthException("CLEANUP_DEFERRED",409);
     });continue;
    }
    if(state.candidateAt().plus(TournamentCleanupPolicy.GRACE).isAfter(now))continue;
    persistence.persistNow(t);
    if(store.trash(id,t.revision,state.candidateAt(),now)){t.archived=true;persistence.forgetArchived(t);registry.remove(t.id);org.slf4j.LoggerFactory.getLogger(getClass()).info("event=tournament_auto_trashed tournamentId={} reason={}",t.id,reason.get());}
   }catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=tournament_cleanup_deferred tournamentId={} errorType={}",t.id,failure.getClass().getSimpleName());}
  }
 }
 public List<Map<String,Object>> list(Authentication auth){accounts.requireAdmin(auth);return store.list();}
 public synchronized void keep(Authentication auth,String id,Long revision,String explanation){
  String reason=com.rpsbattle.admin.AdminDirectoryService.reason(explanation);var t=registry.findById(id).orElseThrow(()->new AuthException("TOURNAMENT_NOT_FOUND",404));authorization.requireManager(auth,t);
  synchronized(t){owned.managedMutation(auth,t,revision,()->{TournamentAudit.append(t,"TOURNAMENT_RETAINED",accounts.requireUser(auth).id().value(),id,reason,null,"KEEP");tournaments.changed(t);});store.retain(UUID.fromString(id),clock.instant());}
 }
 public synchronized void restore(Authentication auth,String id,String explanation){
  var actor=accounts.requireAdmin(auth);String reason=com.rpsbattle.admin.AdminDirectoryService.reason(explanation);UUID key;try{key=UUID.fromString(id);}catch(IllegalArgumentException invalid){throw new AuthException("INVALID_ID",400);}
  store.restore(key,clock.instant());persistence.restore();var t=registry.findById(id).orElseThrow(()->new AuthException("TOURNAMENT_NOT_FOUND",404));
  owned.managedMutation(auth,t,t.revision,()->{TournamentAudit.append(t,"TOURNAMENT_RESTORED",actor.id().value(),id,reason,"TRASH","PUBLIC");tournaments.changed(t);});
 }
}
