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
        return create(auth,name,format,visibility,false,10);
    }
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility,boolean checkInRequired,Integer maxParticipants) {
        return create(auth,name,format,visibility,checkInRequired,maxParticipants,4,null);
    }
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility,boolean checkInRequired,Integer maxParticipants,Integer concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order) {
        var actor=accounts.requireUser(auth);
        validateRules(concurrency==null?4:concurrency,order);
        int limit=maxParticipants==null?10:maxParticipants;
        if(limit<2 || limit>32)throw new DemoRequestException("INVALID_PARTICIPANT_LIMIT");
        if(visibility!=null && !visibility.equals("PUBLIC")) throw new AuthException("UNSUPPORTED_TOURNAMENT_VISIBILITY",400);
        var t=tournaments.create(name,format==null?TournamentFormat.ROUND_ROBIN:format);
        synchronized(t) {
            t.maxConcurrentMatches=concurrency==null?4:concurrency;if(order!=null)t.tieBreakOrder=List.copyOf(order);
            t.ownerUserId=actor.id().value();t.checkInRequired=checkInRequired;t.maxParticipants=limit;t.deferNotifications=true;
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
            var registrations=List.copyOf(t.registrations);var checkIn=t.checkInRequired;var capacity=t.maxParticipants;var strategy=t.seedingStrategy;var randomSeed=t.seedingSeed;
            var participants=List.copyOf(t.participants);var matches=List.copyOf(t.matches);
            var details=com.rpsbattle.tournament.persistence.TournamentDetails.snapshot(t);
            t.deferNotifications=true;
            try {mutation.run();persistence.persistNow(t);}
            catch(RuntimeException failure) {
                t.status=status;t.startedAt=started;t.finishedAt=finished;t.statusChangedAt=changedAt;t.revision=revision;
                t.registrations.clear();t.registrations.addAll(registrations);t.checkInRequired=checkIn;t.maxParticipants=capacity;t.seedingStrategy=strategy;t.seedingSeed=randomSeed;
                t.participants.clear();t.participants.addAll(participants);t.matches.clear();t.matches.addAll(matches);details.restore(t);throw failure;
            } finally {t.deferNotifications=false;}
            tournaments.notifyChanged(t);
        }
    }
    public void add(Authentication auth,String id,String botId,String displayName,Long seed) {
        var tournament=tournaments.find(id);
        if(tournament.status==TournamentStatus.REGISTRATION){register(auth,id,botId);return;}
        accounts.requireUser(auth);var bot=bots.execution(auth,botId);var owner=bots.require(auth,botId).ownerUserId();var t=tournaments.find(id);
        mutate(t,()-> {
            if(t.status!=TournamentStatus.DRAFT)throw new DemoRequestException("TOURNAMENT_LOCKED");
            tournaments.add(id,bot.id(),bot.token(),displayName,seed);
            var created=t.participants.getLast();
            String registrationId=t.registrations.stream().filter(r->r.botId().toString().equals(botId)).map(r->r.id().toString()).findFirst().orElse(created.participantId());
            var participant=new TournamentParticipant(registrationId,created.botId(),created.botToken(),created.displayName(),created.seed(),owner);
            var hash=bots.freeze(botId,null);participant=participant.withHash(hash);
            t.participants.set(t.participants.size()-1,participant);
            t.participants.sort(Comparator.comparing(TournamentParticipant::participantId));
            var now=java.time.Instant.now();
            t.registrations.removeIf(r->r.botId().toString().equals(botId));
            t.registrations.add(new TournamentRegistration(UUID.fromString(participant.participantId()),UUID.fromString(id),owner,UUID.fromString(botId),UUID.fromString(botId),participant.displayName(),TournamentRegistration.Status.APPROVED,now,now,null).withLock(hash,now));
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
            for(int i=0;i<t.registrations.size();i++)if(t.registrations.get(i).id().toString().equals(participantId))t.registrations.set(i,t.registrations.get(i).withStatus(TournamentRegistration.Status.CANCELLED,java.time.Instant.now()));
        });
    }
    public void start(Authentication auth,String id) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);var scores=historicalScores();
        synchronized(t) {
            var reserved=new ArrayList<String>();
            try {
                mutate(t,()-> {
                    if(t.checkInRequired && t.status!=TournamentStatus.READY)throw new DemoRequestException("CHECK_IN_REQUIRED");
                    prepare(t,scores);tournaments.start(id);
                    if(queue!=null) {var ids=t.format==TournamentFormat.SINGLE_ELIMINATION?t.bracket.stream().map(BracketNode::matchId).filter(Objects::nonNull).toList():t.matches.stream().map(m->m.id).toList();queue.reserve(ids);reserved.addAll(ids);}
                });
            } catch(RuntimeException failure) {if(queue!=null)queue.release(reserved);throw failure;}
        }
    }
    public void cancel(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,()->tournaments.cancel(id));}
    public void openRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,()->tournaments.openRegistration(id));}
    public void closeRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);var scores=historicalScores();mutate(t,()->{
        if(t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("INVALID_TOURNAMENT_TRANSITION");
        if(t.participants.size()<2)throw new DemoRequestException("NOT_ENOUGH_BOTS");
        for(int i=0;i<t.registrations.size();i++)if(t.registrations.get(i).status()==TournamentRegistration.Status.PENDING)t.registrations.set(i,t.registrations.get(i).withStatus(TournamentRegistration.Status.REJECTED,java.time.Instant.now()));
        if(!t.checkInRequired)prepare(t,scores);
        TournamentStateMachine.transition(t,t.checkInRequired?TournamentStatus.CHECK_IN:TournamentStatus.READY,java.time.Instant.now());tournaments.changed(t);
    });}
    public void flush() {persistence.flushForRead();}

    private Map<String,com.rpsbattle.tournament.seeding.SeedingService.Score> historicalScores() {
        var scores=new HashMap<String,com.rpsbattle.tournament.seeding.SeedingService.Score>();
        // Snapshot completed tournaments one at a time, before taking the target tournament monitor.
        for(var cup:registry.findAll())synchronized(cup) {if(cup.status!=TournamentStatus.FINISHED)continue;
            var ranking=tournaments.ranking(cup);
            for(var row:ranking)cup.participants.stream().filter(p->p.participantId().equals(row.participantId())).findFirst().ifPresent(p->scores.merge(p.botId(),new com.rpsbattle.tournament.seeding.SeedingService.Score(row.points(),row.wins(),row.piecesRemaining()),com.rpsbattle.tournament.seeding.SeedingService.Score::plus));
        }return Map.copyOf(scores);
    }
    private void prepare(Tournament t,Map<String,com.rpsbattle.tournament.seeding.SeedingService.Score> scores) {
        for(int i=0;i<t.participants.size();i++) {
            var p=t.participants.get(i);String hash=p.artifactHash();
            if(hash==null) {hash=bots.freeze(p.botId(),null);t.participants.set(i,p.withHash(hash));
                for(int j=0;j<t.registrations.size();j++)if(t.registrations.get(j).id().toString().equals(p.participantId()))t.registrations.set(j,t.registrations.get(j).withLock(hash,java.time.Instant.now()));
            }else bots.verifyFrozen(p.botId(),hash);
        }
        if(t.participants.stream().anyMatch(p->p.seedNumber()==null)) {
            var seeded=com.rpsbattle.tournament.seeding.SeedingService.seed(t.participants,t.seedingStrategy,t.seedingSeed,scores);t.participants.clear();t.participants.addAll(seeded);
        }
    }
    public void configureSeeding(Authentication auth,String id,SeedingStrategy strategy,Long seed,Map<String,Integer> manual) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        mutate(t,()->{
            if(!List.of(TournamentStatus.DRAFT,TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status))throw new DemoRequestException("TOURNAMENT_LOCKED");
            if(strategy==null)throw new DemoRequestException("INVALID_MANUAL_SEEDS");
            if(strategy==SeedingStrategy.MANUAL) {
                if(manual==null || manual.size()!=t.participants.size() || !manual.keySet().equals(t.participants.stream().map(TournamentParticipant::participantId).collect(java.util.stream.Collectors.toSet())))throw new DemoRequestException("INVALID_MANUAL_SEEDS");
                var used=new HashSet<Integer>();for(var value:manual.values())if(value==null || value<1 || value>t.participants.size() || !used.add(value))throw new DemoRequestException("INVALID_MANUAL_SEEDS");
            }
            t.seedingStrategy=strategy;if(seed!=null)t.seedingSeed=seed;
            for(int i=0;i<t.participants.size();i++){var p=t.participants.get(i);t.participants.set(i,p.withSeeding(null,strategy==SeedingStrategy.MANUAL?manual.get(p.participantId()):null));}
            tournaments.changed(t);
        });
    }

    private static void validateRules(int concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order) {
        if(concurrency<1||concurrency>4)throw new DemoRequestException("INVALID_CONCURRENCY");
        if(order!=null && (order.isEmpty() || order.size()>5 || order.stream().anyMatch(Objects::isNull) || order.getFirst()!=com.rpsbattle.tournament.ranking.TieBreak.POINTS || new HashSet<>(order).size()!=order.size()))throw new DemoRequestException("INVALID_TIE_BREAK_ORDER");
    }
    public void configureRules(Authentication auth,String id,int concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);validateRules(concurrency,order);
        mutate(t,()->{if(!List.of(TournamentStatus.DRAFT,TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status))throw new DemoRequestException("TOURNAMENT_LOCKED");
            t.maxConcurrentMatches=concurrency;if(order!=null)t.tieBreakOrder=List.copyOf(order);tournaments.changed(t);
        });
    }
    private void registrationPhase(Tournament t) {
        if(t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("TOURNAMENT_LOCKED");
    }
    private int registrationIndex(Tournament t,String registrationId) {
        for(int i=0;i<t.registrations.size();i++)if(t.registrations.get(i).id().toString().equals(registrationId))return i;
        throw new DemoRequestException("REGISTRATION_NOT_FOUND");
    }
    public void register(Authentication auth,String id,String botId) {
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);
        mutate(t,()->{
            registrationPhase(t);
            var bot=bots.requireRegistrationBot(auth,botId);
            if(!actor.id().value().equals(bot.ownerUserId()))throw new AuthException("BOT_ACCESS_DENIED",403);
            var previous=t.registrations.stream().filter(r->r.botId().toString().equals(botId)).findFirst();
            if(previous.isPresent() && previous.get().active())throw new DemoRequestException("DUPLICATE_PARTICIPANT");
            if(t.registrations.stream().filter(TournamentRegistration::active).count()>=t.maxParticipants)throw new DemoRequestException("PARTICIPANT_LIMIT");
            var key=previous.map(TournamentRegistration::id).orElseGet(UUID::randomUUID);
            t.registrations.removeIf(r->r.id().equals(key));
            t.registrations.add(new TournamentRegistration(key,UUID.fromString(id),actor.id().value(),bot.id(),bot.id(),bot.name(),TournamentRegistration.Status.PENDING,java.time.Instant.now(),null,null).withSourceHash(bots.sourceHash(botId)));
            tournaments.changed(t);
        });
    }
    public void review(Authentication auth,String id,String registrationId,boolean approve) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        mutate(t,()->{
            registrationPhase(t);int index=registrationIndex(t,registrationId);var r=t.registrations.get(index);
            if(r.status()!=TournamentRegistration.Status.PENDING && !(r.status()==TournamentRegistration.Status.APPROVED && !approve))throw new DemoRequestException("INVALID_REGISTRATION_TRANSITION");
            if(approve) {
                var bot=bots.requireRegistrationBotForManager(auth,r.botId().toString());
                if(!r.userId().equals(bot.ownerUserId()))throw new DemoRequestException("BOT_ACCESS_DENIED");
                var hash=bots.freeze(r.botVersionId().toString(),r.sourceHash());r=r.withLock(hash,java.time.Instant.now());
                var execution=bots.registrationExecution(r.botId().toString());
                t.participants.add(new TournamentParticipant(r.id().toString(),r.botId().toString(),execution.token(),r.displayName(),UUID.randomUUID().getMostSignificantBits(),r.userId()).withHash(hash));
                t.participants.sort(Comparator.comparing(TournamentParticipant::participantId));
            } else t.participants.removeIf(p->p.participantId().equals(registrationId));
            t.registrations.set(index,r.withStatus(approve?TournamentRegistration.Status.APPROVED:TournamentRegistration.Status.REJECTED,java.time.Instant.now()));tournaments.changed(t);
        });
    }
    public void withdraw(Authentication auth,String id,String registrationId) {
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);
        mutate(t,()->{
            if(t.status!=TournamentStatus.REGISTRATION && t.status!=TournamentStatus.CHECK_IN)throw new DemoRequestException("TOURNAMENT_LOCKED");
            int index=registrationIndex(t,registrationId);var r=t.registrations.get(index);
            if(!r.userId().equals(actor.id().value()) && !authorization.manages(auth,t))throw new AuthException("TOURNAMENT_FORBIDDEN",403);
            if(!r.active())throw new DemoRequestException("INVALID_REGISTRATION_TRANSITION");
            t.registrations.set(index,r.withStatus(TournamentRegistration.Status.CANCELLED,java.time.Instant.now()));
            t.participants.removeIf(p->p.participantId().equals(registrationId));tournaments.changed(t);
        });
    }
    public void checkIn(Authentication auth,String id,String registrationId) {
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);
        mutate(t,()->{
            if(t.status!=TournamentStatus.CHECK_IN || !t.checkInRequired)throw new DemoRequestException("CHECK_IN_NOT_OPEN");
            if(registrationId!=null) {
                var r=t.registrations.get(registrationIndex(t,registrationId));
                if(!r.userId().equals(actor.id().value()))throw new AuthException("TOURNAMENT_FORBIDDEN",403);
                if(r.status()!=TournamentRegistration.Status.APPROVED && r.status()!=TournamentRegistration.Status.CHECKED_IN)throw new DemoRequestException("INVALID_REGISTRATION_TRANSITION");
            }
            boolean found=false;
            for(int i=0;i<t.registrations.size();i++) {
                var r=t.registrations.get(i);
                if(r.userId().equals(actor.id().value()) && (registrationId==null || r.id().toString().equals(registrationId)) && (r.status()==TournamentRegistration.Status.APPROVED || r.status()==TournamentRegistration.Status.CHECKED_IN)) {
                    found=true;if(r.status()==TournamentRegistration.Status.APPROVED)t.registrations.set(i,r.withStatus(TournamentRegistration.Status.CHECKED_IN,java.time.Instant.now()));
                }
            }
            if(!found)throw new DemoRequestException("NO_APPROVED_REGISTRATION");tournaments.changed(t);
        });
    }
    public void closeCheckIn(Authentication auth,String id) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);var scores=historicalScores();
        mutate(t,()->{
            if(t.status!=TournamentStatus.CHECK_IN)throw new DemoRequestException("CHECK_IN_NOT_OPEN");
            if(t.registrations.stream().filter(r->r.status()==TournamentRegistration.Status.CHECKED_IN).count()<2)throw new DemoRequestException("NOT_ENOUGH_CHECKED_IN_BOTS");
            for(int i=0;i<t.registrations.size();i++) {
                var r=t.registrations.get(i);
                if(r.status()==TournamentRegistration.Status.APPROVED){t.registrations.set(i,r.withStatus(TournamentRegistration.Status.NOT_CHECKED_IN,java.time.Instant.now()));t.participants.removeIf(p->p.participantId().equals(r.id().toString()));}
            }
            prepare(t,scores);TournamentStateMachine.transition(t,TournamentStatus.READY,java.time.Instant.now());tournaments.changed(t);
        });
    }
}
