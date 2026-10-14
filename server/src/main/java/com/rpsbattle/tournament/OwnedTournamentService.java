package com.rpsbattle.tournament;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.online.DemoRequestException;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.TournamentPersistenceCoordinator;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OwnedTournamentService {
    private final TournamentService tournaments;
    private final TournamentRegistry registry;
    private final TournamentPersistenceCoordinator persistence;
    private final AccountAuthorizationService accounts;
    private final TournamentAuthorizationService authorization;
    private final OwnedBotService bots;
    private com.rpsbattle.worker.ExecutionQueue queue;
    @org.springframework.beans.factory.annotation.Autowired
    public void executionQueue(com.rpsbattle.worker.ExecutionQueue queue) {this.queue=queue;}
    public OwnedTournamentService(TournamentService tournaments,TournamentRegistry registry,TournamentPersistenceCoordinator persistence,AccountAuthorizationService accounts,TournamentAuthorizationService authorization,OwnedBotService bots) {this.tournaments=tournaments;this.registry=registry;this.persistence=persistence;this.accounts=accounts;this.authorization=authorization;this.bots=bots;}
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility) {
        var actor=accounts.requireUser(auth);
        if(visibility!=null && !visibility.equals("PUBLIC")) throw new AuthException("UNSUPPORTED_TOURNAMENT_VISIBILITY",400);
        var t=tournaments.create(name,format==null?TournamentFormat.ROUND_ROBIN:format);
        synchronized(t) {
            t.ownerUserId=actor.id().value();t.deferNotifications=true;
            try {persistence.persistNow(t);} catch(RuntimeException failure) {registry.remove(t.id);throw failure;}
            finally {t.deferNotifications=false;}
            tournaments.notifyChanged(t);
        }
        return t;
    }
    /** Rollback memory on commit failure; scheduler and notifications cannot see staged changes. */
    private void mutate(Tournament t,Runnable mutation) {
        synchronized(t) {
            var status=t.status;var started=t.startedAt;var finished=t.finishedAt;var changedAt=t.statusChangedAt;long revision=t.revision;
            var participants=List.copyOf(t.participants);var matches=List.copyOf(t.matches);
            t.deferNotifications=true;
            try {mutation.run();persistence.persistNow(t);}
            catch(RuntimeException failure) {
                t.status=status;t.startedAt=started;t.finishedAt=finished;t.statusChangedAt=changedAt;t.revision=revision;
                t.participants.clear();t.participants.addAll(participants);t.matches.clear();t.matches.addAll(matches);throw failure;
            } finally {t.deferNotifications=false;}
            tournaments.notifyChanged(t);
        }
    }
    public void add(Authentication auth,String id,String botId,String displayName,Long seed) {
        accounts.requireUser(auth);var bot=bots.execution(auth,botId);var owner=bots.require(auth,botId).ownerUserId();var t=tournaments.find(id);
        mutate(t,()-> {
            tournaments.add(id,bot.id(),bot.token(),displayName,seed);
            var participant=t.participants.getLast();t.participants.set(t.participants.size()-1,new TournamentParticipant(participant.participantId(),participant.botId(),participant.botToken(),participant.displayName(),participant.seed(),owner));
            t.participants.sort(Comparator.comparing(TournamentParticipant::participantId));
        });
    }
    public void remove(Authentication auth,String id,String participantId) {
        accounts.requireUser(auth);var t=tournaments.find(id);
        mutate(t,()-> {
            if(!authorization.manages(auth,t)) {
                var participant=t.participants.stream().filter(p->p.participantId().equals(participantId)).findFirst().orElseThrow(()->new DemoRequestException("PARTICIPANT_NOT_FOUND"));
                bots.authorizeWithdrawal(auth,participant.botId());
            }
            tournaments.remove(id,participantId);
        });
    }
    public void start(Authentication auth,String id) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        synchronized(t) {
            var reserved=new ArrayList<String>();
            try {
                mutate(t,()-> {
                    tournaments.start(id);
                    if(queue!=null) {var ids=t.matches.stream().map(m->m.id).toList();queue.reserve(ids);reserved.addAll(ids);}
                });
            } catch(RuntimeException failure) {if(queue!=null)queue.release(reserved);throw failure;}
        }
    }
    public void cancel(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,()->tournaments.cancel(id));}
    public void openRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,()->tournaments.openRegistration(id));}
    public void closeRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,()->tournaments.closeRegistration(id));}
    public void flush() {persistence.flushForRead();}
}
