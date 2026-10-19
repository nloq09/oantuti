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
    private com.rpsbattle.tournament.coding.SubmissionStore submissions;
    private org.springframework.transaction.support.TransactionTemplate submissionTransactions;
    private final ThreadLocal<Boolean> submissionTransaction=new ThreadLocal<>();
    @org.springframework.beans.factory.annotation.Autowired public void submissionSupport(com.rpsbattle.tournament.coding.SubmissionStore store,org.springframework.transaction.PlatformTransactionManager manager){submissions=store;submissionTransactions=new org.springframework.transaction.support.TransactionTemplate(manager);}
    /** Commit registration, version promotion and submission in one DB transaction; restore memory on commit failure. */
    public void submissionMutation(Tournament t,Authentication auth,Runnable change){synchronized(t){var details=com.rpsbattle.tournament.persistence.TournamentDetails.snapshot(t);var registrations=List.copyOf(t.registrations);var participants=List.copyOf(t.participants);long revision=t.revision;try{submissionTransaction.set(true);submissionTransactions.executeWithoutResult(tx->mutate(t,auth,change));tournaments.notifyChanged(t);}catch(RuntimeException failure){details.restore(t);t.registrations.clear();t.registrations.addAll(registrations);t.participants.clear();t.participants.addAll(participants);t.revision=revision;throw failure;}finally{submissionTransaction.remove();}}}
    private final ThreadLocal<String> administrativeReason=new ThreadLocal<>();
    /** Scope the audit explanation while reusing the existing lifecycle transactions and state machine. */
    public void adminLifecycle(Authentication auth,String id,Long revision,String action,String reason,String registrationId){
        accounts.requireAdmin(auth);var t=tournaments.find(id);synchronized(t){
            if(revision==null||revision!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            administrativeReason.set(com.rpsbattle.admin.AdminDirectoryService.reason(reason));
            try{switch(action){case "open-registration"->openRegistration(auth,id);case "close-registration"->closeRegistration(auth,id);case "close-check-in"->closeCheckIn(auth,id);case "start"->start(auth,id);case "cancel"->cancel(auth,id);case "approve"->review(auth,id,registrationId,true);case "reject"->review(auth,id,registrationId,false);default->throw new AuthException("INVALID_ACTION",400);}}
            finally{administrativeReason.remove();}
        }
    }
    private final TournamentService tournaments;
    private final TournamentRegistry registry;
    private final TournamentPersistenceCoordinator persistence;
    private final AccountAuthorizationService accounts;
    private final TournamentAuthorizationService authorization;
    private final OwnedBotService bots;
    private java.time.Clock clock=java.time.Clock.systemUTC();
    @org.springframework.beans.factory.annotation.Autowired public void clock(java.time.Clock clock){this.clock=clock;}
    public void configureTiming(Authentication auth,String id,Long revision,TournamentTiming timing){var t=tournaments.find(id);timing.validate();managedMutation(auth,t,revision,()->{if(t.status!=TournamentStatus.DRAFT||!t.registrations.isEmpty())throw new DemoRequestException("TOURNAMENT_LOCKED");t.timing=timing;TournamentAudit.append(t,"TIMING_CONFIGURED",actor(auth),id,null,null,null);tournaments.changed(t);});}
    public void configureTimeline(Authentication auth,String id,Long revision,TournamentTimeline timeline){
        var t=tournaments.find(id);
        managedMutation(auth,t,revision,()->{
            if(timeline==null)throw new DemoRequestException("INVALID_TOURNAMENT_TIMELINE");
            if(t.status!=TournamentStatus.DRAFT||!t.registrations.isEmpty()||!t.participants.isEmpty())throw new DemoRequestException("TOURNAMENT_LOCKED");
            String before=t.timeline==null?null:t.timeline.toString();t.timeline=timeline;
            TournamentAudit.append(t,"TIMELINE_CONFIGURED",actor(auth),id,null,before,timeline.toString());tournaments.changed(t);
        });
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=1000) public void expireCheckIns(){
        for(var t:registry.findAll()){if(t.status!=TournamentStatus.CHECK_IN||t.timing.checkInDeadline()==null||clock.instant().isBefore(t.timing.checkInDeadline()))continue;var scores=historicalScores();synchronized(t){
            if(t.status!=TournamentStatus.CHECK_IN||t.timing.checkInDeadline()==null||clock.instant().isBefore(t.timing.checkInDeadline()))continue;
            try{mutate(t,null,()->{for(int i=0;i<t.registrations.size();i++){var r=t.registrations.get(i);if(r.status()==TournamentRegistration.Status.APPROVED){t.registrations.set(i,r.withStatus(TournamentRegistration.Status.NOT_CHECKED_IN,clock.instant()));t.participants.removeIf(p->p.participantId().equals(r.id().toString()));}}
                boolean enough=t.participants.size()>=(t.format==TournamentFormat.GROUP_KNOCKOUT?4:t.format==TournamentFormat.TEAM_ROUND_ROBIN?6:2);if(enough&&t.format==TournamentFormat.TEAM_ROUND_ROBIN){try{com.rpsbattle.tournament.scheduler.TeamScheduler.validateRoster(t);}catch(DemoRequestException incomplete){enough=false;}}if(enough&&t.format==TournamentFormat.GROUP_KNOCKOUT){try{GroupRoundPlan.validate(t.groupPlans,t.participants.size());}catch(DemoRequestException incomplete){enough=false;}}if(enough){prepare(t,scores);lockRules(t);}TournamentStateMachine.transition(t,enough?TournamentStatus.READY:TournamentStatus.CANCELLED,clock.instant());TournamentAudit.append(t,enough?"TOURNAMENT_READY":"TOURNAMENT_CANCELLED",null,t.id,"CHECK_IN_DEADLINE",null,t.status.name());tournaments.changed(t);
            });}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=check_in_expiry_failed tournamentId={}",t.id);}
        }}
    }
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
        return create(auth,name,format,visibility,checkInRequired,maxParticipants,concurrency,order,ParticipantPolicy.BOT_ONLY);
    }
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility,boolean checkInRequired,Integer maxParticipants,Integer concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order,ParticipantPolicy policy) {
        return create(auth,name,format,visibility,checkInRequired,maxParticipants,concurrency,order,policy,1);
    }
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility,boolean checkInRequired,Integer maxParticipants,Integer concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order,ParticipantPolicy policy,Integer bestOf) {return create(auth,name,format,visibility,checkInRequired,maxParticipants,concurrency,order,policy,bestOf,null);}
    public Tournament create(Authentication auth,String name,TournamentFormat format,String visibility,boolean checkInRequired,Integer maxParticipants,Integer concurrency,List<com.rpsbattle.tournament.ranking.TieBreak> order,ParticipantPolicy policy,Integer bestOf,TournamentBotPolicy botPolicy) {
        boolean enroll=botPolicy!=null;int seriesLength=TournamentSeries.validate(bestOf);
        var actor=accounts.requireUser(auth);botPolicy=botPolicy==null?TournamentBotPolicy.defaults():botPolicy;
        if(policy==ParticipantPolicy.HUMAN_ONLY&&botPolicy.submissionMode()!=BotSubmissionMode.PRE_UPLOADED_ONLY)throw new DemoRequestException("INVALID_BOT_SUBMISSION_POLICY");
        validateRules(concurrency==null?4:concurrency,order);
        int limit=maxParticipants==null?10:maxParticipants;
        if(limit<(format==TournamentFormat.GROUP_KNOCKOUT?4:format==TournamentFormat.TEAM_ROUND_ROBIN?6:2) || limit>32)throw new DemoRequestException("INVALID_PARTICIPANT_LIMIT");
        if(visibility!=null && !visibility.equals("PUBLIC")) throw new AuthException("UNSUPPORTED_TOURNAMENT_VISIBILITY",400);
        var t=tournaments.create(name,format==null?TournamentFormat.ROUND_ROBIN:format);
        synchronized(t) {
            if(format==TournamentFormat.SWISS&&order==null)t.tieBreakOrder=List.of(com.rpsbattle.tournament.ranking.TieBreak.POINTS,com.rpsbattle.tournament.ranking.TieBreak.BUCHHOLZ,com.rpsbattle.tournament.ranking.TieBreak.WINS,com.rpsbattle.tournament.ranking.TieBreak.REMAINING_PIECES);
            t.botPolicy=botPolicy;t.bestOf=seriesLength;t.maxConcurrentMatches=concurrency==null?4:concurrency;if(order!=null)t.tieBreakOrder=List.copyOf(order);
            TournamentAudit.append(t,"TOURNAMENT_CREATED",actor.id().value(),t.id,null,null,t.format.name());
            t.ownerUserId=actor.id().value();t.participantPolicy=policy==null?ParticipantPolicy.BOT_ONLY:policy;t.checkInRequired=checkInRequired;t.maxParticipants=limit;t.deferNotifications=true;
            try {if(submissions!=null&&enroll)submissionTransactions.executeWithoutResult(tx->{persistence.persistNow(t);submissions.enable(t.id);});else persistence.persistNow(t);} catch(RuntimeException failure) {registry.remove(t.id);throw failure;}
            finally {t.deferNotifications=false;}
            tournaments.notifyChanged(t);
        }
        return t;
    }
    public void configureBotPolicy(Authentication auth,String id,Long revision,TournamentBotPolicy policy){var t=tournaments.find(id);managedMutation(auth,t,revision,()->{if(policy==null)throw new DemoRequestException("INVALID_BOT_SUBMISSION_POLICY");if(t.status!=TournamentStatus.DRAFT||!t.registrations.isEmpty()||!t.participants.isEmpty())throw new DemoRequestException("BOT_POLICY_LOCKED");if(t.participantPolicy==ParticipantPolicy.HUMAN_ONLY&&policy.submissionMode()!=BotSubmissionMode.PRE_UPLOADED_ONLY)throw new DemoRequestException("INVALID_BOT_SUBMISSION_POLICY");t.botPolicy=policy;TournamentAudit.append(t,"BOT_POLICY_UPDATED",actor(auth),id,null,null,policy.submissionMode().name());tournaments.changed(t);});}
    public UUID actor(Authentication auth){return accounts.requireUser(auth).id().value();}
    public void stagePlan(Authentication auth,String id,Long revision,List<GroupRoundPlan> plans){var t=tournaments.find(id);managedMutation(auth,t,revision,()->{if(t.format!=TournamentFormat.GROUP_KNOCKOUT||t.status!=TournamentStatus.DRAFT||!t.registrations.isEmpty()||!t.matches.isEmpty())throw new DemoRequestException("TOURNAMENT_LOCKED");GroupRoundPlan.validate(plans,t.maxParticipants);t.groupPlans=List.copyOf(plans);TournamentAudit.append(t,"STAGE_PLAN_UPDATED",actor(auth),id,null,null,Integer.toString(plans.size()));tournaments.changed(t);});}
    void systemMutation(Tournament t,Runnable operation){mutate(t,null,operation);}
    public void managedMutation(Authentication auth,Tournament t,Long expected,Runnable operation){
        authorization.requireManager(auth,t);synchronized(t){
            if(expected==null||expected!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            var fresh=new ArrayList<String>();var before=t.status;
            try{mutate(t,auth,()->{operation.run();if(queue!=null&&before!=TournamentStatus.RUNNING&&t.status==TournamentStatus.RUNNING){var already=queue.reservedIds();var ids=t.format==TournamentFormat.SINGLE_ELIMINATION?t.bracket.stream().map(BracketNode::matchId).filter(Objects::nonNull).filter(key->t.matches.stream().noneMatch(m->m.id.equals(key)&&m.status==TournamentMatch.Status.FINISHED)).toList():t.matches.stream().filter(m->m.status==TournamentMatch.Status.PENDING).map(m->m.id).toList();queue.reserve(ids);ids.stream().filter(key->!already.contains(key)).forEach(fresh::add);}});}
            catch(RuntimeException failure){if(queue!=null)queue.release(fresh);throw failure;}
        }
    }
    public void verifyArtifacts(Tournament t){for(var p:t.participants)if(t.operations.active(p.participantId())&&p.artifactHash()!=null)bots.verifyFrozen(p.botId(),p.artifactHash());
        if(online!=null&&!t.operations.rulesSnapshot().isEmpty())for(var entry:online.ruleConfiguration().entrySet())if(!entry.getValue().toString().equals(String.valueOf(t.operations.rulesSnapshot().get(entry.getKey()))))throw new DemoRequestException("RULE_SNAPSHOT_MISMATCH");
    }
    /** Rollback memory on commit failure; scheduler and notifications cannot see staged changes. */
    private void mutate(Tournament t,Authentication auth,Runnable mutation) {
        synchronized(t) {
            var status=t.status;var started=t.startedAt;var finished=t.finishedAt;var changedAt=t.statusChangedAt;long revision=t.revision;
            var registrations=List.copyOf(t.registrations);var checkIn=t.checkInRequired;var capacity=t.maxParticipants;var strategy=t.seedingStrategy;var randomSeed=t.seedingSeed;
            var participants=List.copyOf(t.participants);var matches=List.copyOf(t.matches);var matchStates=t.matches.stream().map(TournamentMatch::copy).toList();
            var details=com.rpsbattle.tournament.persistence.TournamentDetails.snapshot(t);
            t.deferNotifications=true;
            try {mutation.run();
                if(t.operations.audit().size()==details.operations().audit().size()) {
                    var actorId=actor(auth);
                    if(status!=t.status){String type=switch(t.status){case REGISTRATION->"REGISTRATION_OPENED";case CHECK_IN->"CHECK_IN_OPENED";case READY->"TOURNAMENT_READY";case RUNNING->"TOURNAMENT_STARTED";case CANCELLED->"TOURNAMENT_CANCELLED";default->"TOURNAMENT_UPDATED";};TournamentAudit.append(t,type,actorId,t.id,administrativeReason.get(),status.name(),t.status.name());}
                    for(var r:t.registrations){var old=registrations.stream().filter(x->x.id().equals(r.id())).findFirst();if(old.isEmpty()||old.get().status()!=r.status()){String type=switch(r.status()){case APPROVED->"PARTICIPANT_APPROVED";case CHECKED_IN->"CHECK_IN";default->"REGISTRATION_UPDATED";};TournamentAudit.append(t,type,actorId,r.id().toString(),administrativeReason.get(),old.map(x->x.status().name()).orElse(null),r.status().name());}}
                    if(t.operations.audit().size()==details.operations().audit().size())TournamentAudit.append(t,"TOURNAMENT_UPDATED",actorId,t.id,null,null,null);
                }
                persistence.persistNow(t);}
            catch(RuntimeException failure) {
                t.status=status;t.startedAt=started;t.finishedAt=finished;t.statusChangedAt=changedAt;t.revision=revision;
                t.registrations.clear();t.registrations.addAll(registrations);t.checkInRequired=checkIn;t.maxParticipants=capacity;t.seedingStrategy=strategy;t.seedingSeed=randomSeed;
                t.participants.clear();t.participants.addAll(participants);t.matches.clear();t.matches.addAll(matches);for(int i=0;i<matches.size();i++)matches.get(i).restore(matchStates.get(i));details.restore(t);throw failure;
            } finally {t.deferNotifications=false;}
            if(!Boolean.TRUE.equals(submissionTransaction.get()))tournaments.notifyChanged(t);
        }
    }
    public void add(Authentication auth,String id,String botId,String displayName,Long seed) {
        var tournament=tournaments.find(id);
        if(tournament.timeline!=null)throw new AuthException("REGISTRATION_PARTICIPANT_ONLY",409);
        if(tournament.botPolicy.submissionMode()==BotSubmissionMode.CODE_IN_PLATFORM_ONLY)throw new DemoRequestException("PLATFORM_CODE_REQUIRED");
        if(!tournament.participantPolicy.bots())throw new DemoRequestException("CONTROLLER_NOT_ALLOWED");
        if(tournament.status==TournamentStatus.REGISTRATION){register(auth,id,botId);return;}
        accounts.requireUser(auth);var bot=bots.execution(auth,botId);var owner=bots.require(auth,botId).ownerUserId();var t=tournaments.find(id);
        mutate(t,auth,()-> {
            if(t.status!=TournamentStatus.DRAFT)throw new DemoRequestException("TOURNAMENT_LOCKED");
            t.botPolicy.requirePreuploaded(bot.language());
            tournaments.add(id,bot.id(),bot.token(),displayName,seed);
            var created=t.participants.getLast();
            String registrationId=t.registrations.stream().filter(r->r.botId()!=null&&r.botId().toString().equals(botId)).map(r->r.id().toString()).findFirst().orElse(created.participantId());
            var participant=new TournamentParticipant(registrationId,created.botId(),created.botToken(),created.displayName(),created.seed(),owner);
            var hash=bots.freeze(botId,null);participant=participant.withHash(hash);
            t.participants.set(t.participants.size()-1,participant);
            t.participants.sort(Comparator.comparing(TournamentParticipant::participantId));
            var now=java.time.Instant.now();
            t.registrations.removeIf(r->r.botId()!=null&&r.botId().toString().equals(botId));
            t.registrations.add(new TournamentRegistration(UUID.fromString(participant.participantId()),UUID.fromString(id),owner,UUID.fromString(botId),UUID.fromString(botId),participant.displayName(),TournamentRegistration.Status.APPROVED,now,now,null).withLock(hash,now));
        });
    }
    public void remove(Authentication auth,String id,String participantId) {
        accounts.requireUser(auth);var t=tournaments.find(id);
        mutate(t,auth,()-> {
            if(!authorization.manages(auth,t)) {
                var participant=t.participants.stream().filter(p->p.participantId().equals(participantId)).findFirst().orElseThrow(()->new DemoRequestException("PARTICIPANT_NOT_FOUND"));
                if(participant.botId()!=null)bots.authorizeWithdrawal(auth,participant.botId());else if(!Objects.equals(participant.ownerUserId(),actor(auth)))throw new AuthException("TOURNAMENT_FORBIDDEN",403);
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
                mutate(t,auth,()-> {
                    if(t.checkInRequired && t.status!=TournamentStatus.READY)throw new DemoRequestException("CHECK_IN_REQUIRED");
                    prepare(t,scores);lockRules(t);tournaments.start(id);
                    if(queue!=null) {var ids=t.format==TournamentFormat.SINGLE_ELIMINATION?t.bracket.stream().map(BracketNode::matchId).filter(Objects::nonNull).toList():t.matches.stream().map(m->m.id).toList();queue.reserve(ids);reserved.addAll(ids);}
                });
            } catch(RuntimeException failure) {if(queue!=null)queue.release(reserved);throw failure;}
        }
    }
    public void cancel(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,auth,()->tournaments.cancel(id));}
    public void openRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);mutate(t,auth,()->tournaments.openRegistration(id));}
    public void closeRegistration(Authentication auth,String id) {var t=tournaments.find(id);authorization.requireManager(auth,t);var scores=historicalScores();mutate(t,auth,()->{
        if(t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("INVALID_TOURNAMENT_TRANSITION");
        if(t.participants.size()<2)throw new DemoRequestException("NOT_ENOUGH_BOTS");
        for(int i=0;i<t.registrations.size();i++)if(t.registrations.get(i).status()==TournamentRegistration.Status.PENDING)t.registrations.set(i,t.registrations.get(i).withStatus(TournamentRegistration.Status.REJECTED,java.time.Instant.now()));
        if(submissions!=null)submissions.lockAll(t.id);if(!t.checkInRequired){prepare(t,scores);lockRules(t);}
        TournamentStateMachine.transition(t,t.checkInRequired?TournamentStatus.CHECK_IN:TournamentStatus.READY,java.time.Instant.now());tournaments.changed(t);
    });}
    public void flush() {persistence.flushForRead();}

    private Map<String,com.rpsbattle.tournament.seeding.SeedingService.Score> historicalScores() {
        var scores=new HashMap<String,com.rpsbattle.tournament.seeding.SeedingService.Score>();
        // Snapshot completed tournaments one at a time, before taking the target tournament monitor.
        for(var cup:registry.findAll())synchronized(cup) {if(cup.status!=TournamentStatus.FINISHED)continue;
            var ranking=tournaments.ranking(cup);
            for(var row:ranking)cup.participants.stream().filter(p->p.participantId().equals(row.participantId())).findFirst().ifPresent(p->scores.merge(p.botId()==null?"human:"+p.ownerUserId():p.botId(),new com.rpsbattle.tournament.seeding.SeedingService.Score(row.points(),row.wins(),row.piecesRemaining()),com.rpsbattle.tournament.seeding.SeedingService.Score::plus));
        }return Map.copyOf(scores);
    }
    private com.rpsbattle.online.OnlineMatchService online;
    @org.springframework.beans.factory.annotation.Autowired public void runtimeRules(com.rpsbattle.online.OnlineMatchService online){this.online=online;}
    private void lockRules(Tournament t){
        if(!t.operations.rulesSnapshot().isEmpty())return;
        var rules=new LinkedHashMap<String,Object>(online==null?Map.of("ruleSet","classic-online-v1","turnSeconds",30L,"maxTurns",200,"pieceQuota",6,"boardRows",9,"boardColumns",9):online.ruleConfiguration());
        if(t.format==TournamentFormat.GROUP_KNOCKOUT){rules.put("groupStages",t.groupPlans);rules.put("groupCount",t.groupPlans.getFirst().groups());rules.put("qualifyPerGroup",t.groupPlans.getFirst().qualifyTopN());rules.put("groupSeeding","SERPENTINE");rules.put("knockoutSeeding","RANK_THEN_GROUP");}
        if(t.format==TournamentFormat.SWISS){rules.put("swissRounds","CEIL_LOG2_ROSTER");rules.put("swissByePoints",3);rules.put("swissRepeatOpponents",false);}
        if(t.format==TournamentFormat.TEAM_ROUND_ROBIN){rules.put("teamSlots",3);rules.put("teamRosterLimit",5);rules.put("teamScoring","FIXTURE_3_1_0_BOARD_2_1_0");}
        rules.put("bestOf",t.bestOf);rules.put("seriesDrawPolicy","MAX_GAMES_THEN_WINS_REMAINING_PIECES_SEED");rules.put("scoring",List.of(3,1,0));rules.put("tieBreakRules",t.tieBreakOrder.stream().map(Enum::name).toList());rules.put("autoAdvanceRounds",t.operations.autoAdvanceRounds());
        t.operations=t.operations.rules(rules);
    }
    private void prepare(Tournament t,Map<String,com.rpsbattle.tournament.seeding.SeedingService.Score> scores) {
        if(submissions!=null)submissions.lockAll(t.id);
        if(t.format==TournamentFormat.TEAM_ROUND_ROBIN)com.rpsbattle.tournament.scheduler.TeamScheduler.validateRoster(t);
        if(t.format==TournamentFormat.GROUP_KNOCKOUT)GroupRoundPlan.validate(t.groupPlans,t.participants.size());
        for(int i=0;i<t.participants.size();i++) {
            var p=t.participants.get(i);String hash=p.artifactHash();
            if(p.botId()==null)continue;
            if(submissions!=null&&submissions.enabled(t.id))submissions.requireLocked(t.id,p.participantId());
            else if(!t.botPolicy.equals(TournamentBotPolicy.defaults()))t.botPolicy.requirePreuploaded(bots.registrationExecution(p.botId()).language());
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
        mutate(t,auth,()->{
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
        mutate(t,auth,()->{if(!List.of(TournamentStatus.DRAFT,TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status))throw new DemoRequestException("TOURNAMENT_LOCKED");
            t.maxConcurrentMatches=concurrency;if(order!=null)t.tieBreakOrder=List.copyOf(order);tournaments.changed(t);
        });
    }
    private void registrationPhase(Tournament t) {
        if(t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("TOURNAMENT_LOCKED");
        if(t.timeline!=null&&TournamentPhaseResolver.resolve(t.status,t.timeline,clock.instant(),null,false).phase()!=TournamentPhase.REGISTRATION_OPEN)throw new AuthException("REGISTRATION_NOT_OPEN",409);
        if(t.timing.registrationDeadline()!=null&&!clock.instant().isBefore(t.timing.registrationDeadline()))throw new DemoRequestException("REGISTRATION_CLOSED");
    }
    private int registrationIndex(Tournament t,String registrationId) {
        for(int i=0;i<t.registrations.size();i++)if(t.registrations.get(i).id().toString().equals(registrationId))return i;
        throw new DemoRequestException("REGISTRATION_NOT_FOUND");
    }
    public void register(Authentication auth,String id,String botId) {
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);
        if(t.timeline!=null)throw new AuthException("REGISTRATION_PARTICIPANT_ONLY",409);
        mutate(t,auth,()->{
            registrationPhase(t);
            if(!t.participantPolicy.bots())throw new DemoRequestException("CONTROLLER_NOT_ALLOWED");
            if(t.botPolicy.submissionMode()==BotSubmissionMode.CODE_IN_PLATFORM_ONLY)throw new DemoRequestException("PLATFORM_CODE_REQUIRED");
            var bot=bots.requireRegistrationBot(auth,botId);t.botPolicy.requirePreuploaded(bot.language());
            if(!actor.id().value().equals(bot.ownerUserId()))throw new AuthException("BOT_ACCESS_DENIED",403);
            var previous=t.registrations.stream().filter(r->r.botId()!=null&&r.botId().toString().equals(botId)).findFirst();
            if(previous.isPresent() && previous.get().active())throw new DemoRequestException("DUPLICATE_PARTICIPANT");
            if(t.registrations.stream().filter(TournamentRegistration::active).count()>=t.maxParticipants)throw new DemoRequestException("PARTICIPANT_LIMIT");
            var key=previous.map(TournamentRegistration::id).orElseGet(UUID::randomUUID);
            t.registrations.removeIf(r->r.id().equals(key));
            t.registrations.add(new TournamentRegistration(key,UUID.fromString(id),actor.id().value(),bot.id(),bot.id(),bot.name(),TournamentRegistration.Status.PENDING,java.time.Instant.now(),null,null).withSourceHash(bots.sourceHash(botId)));
            tournaments.changed(t);
        });
    }
    public void registerHuman(Authentication auth,String id,boolean accepted){
        var actor=accounts.requireUser(auth);var t=tournaments.find(id);
        if(t.timeline!=null)throw new AuthException("REGISTRATION_PARTICIPANT_ONLY",409);
        if(!accepted)throw new DemoRequestException("RULES_ACCEPTANCE_REQUIRED");
        mutate(t,auth,()->{registrationPhase(t);if(!t.participantPolicy.humans())throw new DemoRequestException("CONTROLLER_NOT_ALLOWED");
            var previous=t.registrations.stream().filter(r->r.botId()==null&&r.userId().equals(actor.id().value())).findFirst();
            if(previous.isPresent()&&previous.get().active())throw new DemoRequestException("DUPLICATE_PARTICIPANT");
            if(t.registrations.stream().filter(TournamentRegistration::active).count()>=t.maxParticipants)throw new DemoRequestException("PARTICIPANT_LIMIT");
            var key=previous.map(TournamentRegistration::id).orElseGet(UUID::randomUUID);t.registrations.removeIf(r->r.id().equals(key));
            t.registrations.add(new TournamentRegistration(key,UUID.fromString(id),actor.id().value(),null,null,actor.displayName(),TournamentRegistration.Status.PENDING,java.time.Instant.now(),null,null));tournaments.changed(t);
        });
    }
    public void review(Authentication auth,String id,String registrationId,boolean approve) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);
        mutate(t,auth,()->{
            registrationPhase(t);int index=registrationIndex(t,registrationId);var r=t.registrations.get(index);
            if(r.status()!=TournamentRegistration.Status.PENDING && !(r.status()==TournamentRegistration.Status.APPROVED && !approve))throw new DemoRequestException("INVALID_REGISTRATION_TRANSITION");
            if(approve&&r.botId()==null){t.participants.add(new TournamentParticipant(r.id().toString(),null,"",r.displayName(),UUID.randomUUID().getMostSignificantBits(),r.userId()));}
            else if(approve) {
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
        mutate(t,auth,()->{
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
        mutate(t,auth,()->{
            if(t.status!=TournamentStatus.CHECK_IN || !t.checkInRequired)throw new DemoRequestException("CHECK_IN_NOT_OPEN");
            if(t.timing.checkInDeadline()!=null&&!clock.instant().isBefore(t.timing.checkInDeadline()))throw new DemoRequestException("CHECK_IN_NOT_OPEN");
            if(registrationId!=null) {
                var r=t.registrations.get(registrationIndex(t,registrationId));
                if(!r.userId().equals(actor.id().value()))throw new AuthException("TOURNAMENT_FORBIDDEN",403);
                if(r.status()!=TournamentRegistration.Status.APPROVED && r.status()!=TournamentRegistration.Status.CHECKED_IN)throw new DemoRequestException("INVALID_REGISTRATION_TRANSITION");
            }
            boolean found=false;
            for(int i=0;i<t.registrations.size();i++) {
                var r=t.registrations.get(i);
                if(r.userId().equals(actor.id().value()) && (registrationId==null || r.id().toString().equals(registrationId)) && (r.status()==TournamentRegistration.Status.APPROVED || r.status()==TournamentRegistration.Status.CHECKED_IN)) {
                    if(r.botId()!=null&&submissions!=null)submissions.requireLocked(t.id,r.id().toString());found=true;if(r.status()==TournamentRegistration.Status.APPROVED)t.registrations.set(i,r.withStatus(TournamentRegistration.Status.CHECKED_IN,java.time.Instant.now()));
                }
            }
            if(!found)throw new DemoRequestException("NO_APPROVED_REGISTRATION");tournaments.changed(t);
        });
    }
    public void closeCheckIn(Authentication auth,String id) {
        var t=tournaments.find(id);authorization.requireManager(auth,t);var scores=historicalScores();
        mutate(t,auth,()->{
            if(t.status!=TournamentStatus.CHECK_IN)throw new DemoRequestException("CHECK_IN_NOT_OPEN");
            if(t.registrations.stream().filter(r->r.status()==TournamentRegistration.Status.CHECKED_IN).count()<2)throw new DemoRequestException("NOT_ENOUGH_CHECKED_IN_BOTS");
            for(int i=0;i<t.registrations.size();i++) {
                var r=t.registrations.get(i);
                if(r.status()==TournamentRegistration.Status.APPROVED){t.registrations.set(i,r.withStatus(TournamentRegistration.Status.NOT_CHECKED_IN,java.time.Instant.now()));t.participants.removeIf(p->p.participantId().equals(r.id().toString()));}
            }
            prepare(t,scores);lockRules(t);TournamentStateMachine.transition(t,TournamentStatus.READY,java.time.Instant.now());tournaments.changed(t);
        });
    }
}
