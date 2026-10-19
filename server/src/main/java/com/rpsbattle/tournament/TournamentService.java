package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.scheduler.TournamentScheduler;
import com.rpsbattle.tournament.ranking.RankingService;
import com.rpsbattle.bot.upload.BotUploadService;
import com.rpsbattle.online.DemoRequestException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
@Service
public class TournamentService {
    private com.rpsbattle.tournament.coding.SubmissionStore submissions;
    @org.springframework.beans.factory.annotation.Autowired public void submissionStore(com.rpsbattle.tournament.coding.SubmissionStore s){submissions=s;}
    private final TournamentRegistry registry;
    private final BotUploadService bots;
    private final TournamentScheduler scheduler;
    private final RankingService ranking;
    private final List<Consumer<Tournament>> listeners=new CopyOnWriteArrayList<>();
    public TournamentService(TournamentRegistry registry,BotUploadService bots,TournamentScheduler scheduler,RankingService ranking) {
        this.registry=registry; this.bots=bots; this.scheduler=scheduler; this.ranking=ranking;
    }
    private com.rpsbattle.online.OnlineMatchService online;
    private com.rpsbattle.account.repository.UserRepository users;
    private final Map<UUID,String> publicNames=new java.util.concurrent.ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired public void liveRooms(com.rpsbattle.online.OnlineMatchService online){this.online=online;}
    @org.springframework.beans.factory.annotation.Autowired public void users(com.rpsbattle.account.repository.UserRepository users){this.users=users;}
    private String username(UUID id){if(id==null||users==null)return "—";return publicNames.computeIfAbsent(id,key->users.findById(new com.rpsbattle.account.domain.UserId(key)).map(com.rpsbattle.account.domain.User::username).orElse("Người chơi"));}
    public Tournament find(String id) { return registry.findById(id).orElseThrow(()->new DemoRequestException("TOURNAMENT_NOT_FOUND")); }
    public List<com.rpsbattle.tournament.ranking.RankingEntry> ranking(Tournament t){return ranking.rank(t);}
    public Tournament create(String name,TournamentFormat format) { return registry.create(name,format); }
    public void authorizeOwner(String id, String token) {
        var t=find(id);
        if (token == null || !t.ownerToken.equals(token)) throw new DemoRequestException("TOURNAMENT_FORBIDDEN");
    }
    public void removeAuthorized(String id, String participantId, String ownerToken, String botToken) {
        var t = find(id);
        synchronized (t) {
            rosterOpen(t);
            var participant = t.participants.stream().filter(p -> p.participantId().equals(participantId))
                    .findFirst().orElseThrow(() -> new DemoRequestException("PARTICIPANT_NOT_FOUND"));
            if (!t.ownerToken.equals(ownerToken)) {
                if (botToken == null || !participant.botToken().equals(botToken)) throw new DemoRequestException("TOURNAMENT_FORBIDDEN");
                bots.authorize(participant.botId(), botToken);
            }
            remove(id, participantId);
        }
    }
    private void rosterOpen(Tournament t) {if(t.status!=TournamentStatus.DRAFT && t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("TOURNAMENT_LOCKED");}
    public void openRegistration(String id) {var t=find(id);synchronized(t){TournamentStateMachine.transition(t,TournamentStatus.REGISTRATION,Instant.now());changed(t);}}
    public void closeRegistration(String id) {var t=find(id);synchronized(t){
        if(t.status!=TournamentStatus.REGISTRATION)throw new DemoRequestException("INVALID_TOURNAMENT_TRANSITION");
        if(t.participants.size()<2)throw new DemoRequestException("NOT_ENOUGH_BOTS");
        TournamentStateMachine.transition(t,TournamentStatus.READY,Instant.now());changed(t);
    }}
    public void add(String id,String botId,String token,String name,Long seed) {
        var t=find(id);
        var bot=bots.authorize(botId,token);
        synchronized(t) {
            rosterOpen(t);
            if(t.participants.size()>=t.maxParticipants) throw new DemoRequestException("PARTICIPANT_LIMIT");
            if(t.participants.stream().anyMatch(p->Objects.equals(p.botId(),botId))) throw new DemoRequestException("DUPLICATE_PARTICIPANT");
            if(name!=null && (name.isBlank()||name.length()>60)) throw new DemoRequestException("INVALID_BOT_NAME");
            t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),botId,token,name==null?bot.name():name.trim(),seed==null?UUID.randomUUID().getMostSignificantBits():seed));
            changed(t);
        }
    }
    public void remove(String id,String participantId) { var t=find(id); synchronized(t) { rosterOpen(t); if(!t.participants.removeIf(p->p.participantId().equals(participantId))) throw new DemoRequestException("PARTICIPANT_NOT_FOUND"); changed(t); } }
    public void start(String id) {
        start(id,Instant.now());
    }
    public void start(String id,Instant now) {
        var t=find(id); synchronized(t) {
            if(t.status!=TournamentStatus.DRAFT && t.status!=TournamentStatus.READY)throw new DemoRequestException("TOURNAMENT_LOCKED");
            if(t.participants.size()<2) throw new DemoRequestException("NOT_ENOUGH_BOTS");
            for(var p:t.participants) { if(p.botId()==null)continue;var b=bots.authorize(p.botId(),p.botToken()); if(!bots.available(b.language())) throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE"); }
            var schedule=scheduler.schedule(t);
            if(t.timeline!=null)for(var match:schedule)match.scheduledAt=now;
            // Preserve the existing quick-start API; it traverses the same legal lifecycle edges.
            if(t.status==TournamentStatus.DRAFT){TournamentStateMachine.transition(t,TournamentStatus.REGISTRATION,now);TournamentStateMachine.transition(t,TournamentStatus.READY,now);}
            t.matches.addAll(schedule);TournamentStateMachine.transition(t,TournamentStatus.RUNNING,now);changed(t);
        }
    }
    public void cancel(String id) { var t=find(id); synchronized(t) {
        if(t.status==TournamentStatus.FINISHED||t.status==TournamentStatus.CANCELLED) throw new DemoRequestException("TOURNAMENT_LOCKED");
        TournamentStateMachine.transition(t,TournamentStatus.CANCELLED,Instant.now());changed(t);
    } }
    public void listen(Consumer<Tournament> listener){listeners.add(listener);}
    public void changed(Tournament t){t.revision++;if(!t.deferNotifications) notifyChanged(t);}
    public void notifyChanged(Tournament t){for(var listener:listeners) {try {listener.accept(t);} catch(RuntimeException ignored){}}}
    public List<Map<String,Object>> list(){ return registry.findAll().stream().sorted(Comparator.comparing((Tournament t)->t.createdAt).reversed()).map(this::view).toList(); }
    public Map<String,Object> report(Tournament t){synchronized(t){
        var view=view(t);var report=new LinkedHashMap<String,Object>();report.put("champion",view.get("champion"));var rows=ranking.rank(t);report.put("runnerUp",rows.size()>1&&t.status==TournamentStatus.FINISHED?rows.get(1):null);
        var durations=t.matches.stream().filter(m->m.status==TournamentMatch.Status.FINISHED&&m.startedAt!=null&&m.finishedAt!=null).map(m->Map.entry(m.id,Math.max(0,java.time.Duration.between(m.startedAt,m.finishedAt).toMillis()))).sorted(Map.Entry.comparingByValue()).toList();
        report.put("totalMatches",t.matches.size());report.put("averageMatchDurationMillis",durations.stream().mapToLong(Map.Entry::getValue).average().orElse(0));report.put("shortestMatch",durations.isEmpty()?null:durations.getFirst());report.put("longestMatch",durations.isEmpty()?null:durations.getLast());
        report.put("totalBotErrors",t.matches.stream().filter(m->"BOT_FAILED".equals(m.failure)).count());report.put("infrastructureErrors",t.matches.stream().filter(m->m.status==TournamentMatch.Status.FAILED).count());report.put("finalStandings",rows);return report;
    }}
    public Map<String,Object> view(Tournament t) { return view(t,t.matches,false); }
    public Map<String,Object> summary(Tournament t) { synchronized(t){return view(t,t.matches.stream().filter(m->m.status==TournamentMatch.Status.RUNNING).limit(20).toList(),true);} }
    public Map<String,Object> view(Tournament t,List<TournamentMatch> selected,boolean compact) { synchronized(t) {
        var data=new LinkedHashMap<String,Object>();
        data.put("id",t.id);data.put("name",t.name);data.put("format",t.format);data.put("status",t.status);
        data.put("timeline",t.timeline);
        data.put("timelineStartIssue",TimelineStartService.issue(t));
        data.put("ownerUserId",t.ownerUserId);data.put("ownerName",username(t.ownerUserId));data.put("visibility","PUBLIC");
        data.put("createdAt",t.createdAt);data.put("startedAt",t.startedAt);data.put("finishedAt",t.finishedAt);data.put("revision",t.revision);
        data.put("statusChangedAt",t.statusChangedAt);
        data.put("seedingStrategy",t.seedingStrategy);data.put("seedingSeed",Long.toString(t.seedingSeed));
        data.put("timing",t.timing);data.put("participantPolicy",t.participantPolicy);data.put("botPolicy",t.botPolicy);data.put("submissionValidationRequired",submissions!=null&&submissions.enabled(t.id));data.put("checkInRequired",t.checkInRequired);data.put("maxParticipants",t.maxParticipants);
        data.put("groups",com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.view(t));data.put("groupStage",t.groupStage);data.put("groupPlans",t.groupPlans);data.put("groupHistory",List.copyOf(t.groupHistory));
        data.put("swiss",t.swiss);data.put("teamCompetition",t.format==TournamentFormat.TEAM_ROUND_ROBIN?com.rpsbattle.tournament.scheduler.TeamScheduler.view(t):null);
        data.put("bestOf",t.bestOf);data.put("series",TournamentSeries.roots(t).stream().filter(m->!compact||selected.stream().anyMatch(game->game.seriesId.equals(m.id))).map(m->TournamentSeries.view(t,m)).toList());
        data.put("maxConcurrentMatches",t.maxConcurrentMatches);data.put("tieBreakOrder",t.tieBreakOrder);
        data.put("rounds",List.copyOf(t.rounds));data.put("bracket",List.copyOf(t.bracket));data.put("stages",List.copyOf(t.stages));
        data.put("currentRound",t.matches.stream().filter(m->m.status==TournamentMatch.Status.PENDING||m.status==TournamentMatch.Status.RUNNING).mapToInt(m->m.round).min().orElse(t.rounds.size()));
        data.put("drawPolicy",Set.of(TournamentFormat.GROUP_KNOCKOUT,TournamentFormat.SINGLE_ELIMINATION).contains(t.format)?"REMAINING_PIECES_THEN_SEED":null);
        data.put("autoAdvanceRounds",t.operations.autoAdvanceRounds());data.put("releasedRound",t.operations.releasedRound());data.put("participantStatuses",t.operations.participants());
        data.put("reviews",t.operations.reviews());data.put("rulesSnapshot",t.operations.rulesSnapshot());data.put("audit",(compact?t.operations.audit().stream().skip(Math.max(0,t.operations.audit().size()-20)):t.operations.audit().stream()).map(e->e.type().equals("PARTICIPANT_APPLICATION_REVIEWED")?new TournamentAuditEvent(e.id(),e.sequence(),e.type(),e.actor(),e.target(),null,e.beforeValue(),e.afterValue(),e.at()):e).toList());data.put("paged",compact);
        var names=new LinkedHashMap<String,String>();for(var participant:t.participants)names.put(participant.participantId(),username(participant.ownerUserId()));data.put("participantOwners",names);
        data.put("registrations",List.copyOf(t.registrations));
        data.put("participants",List.copyOf(t.participants));
        // Copy mutable matches while holding the monitor; never expose internal tokens or mutable objects.
        data.put("matches",selected.stream().map(m->{var row=new LinkedHashMap<String,Object>();
            row.put("id",m.id);row.put("participantA",m.participantA);row.put("participantB",m.participantB);row.put("onlineMatchId",m.onlineMatchId);row.put("historyMatchId",m.historyMatchId);
            row.put("status",m.status);row.put("winnerParticipantId",m.winnerParticipantId);row.put("result",m.result);row.put("failure",m.failure);
            row.put("attempt",m.attempt);row.put("decisionReason",m.decisionReason);
            if(m.status==TournamentMatch.Status.RUNNING && online!=null)try{row.put("turn",online.find(m.onlineMatchId).state().turnNumber());}catch(RuntimeException ignored){}
            row.put("stageNumber",com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.stage(t,m.round));row.put("group",com.rpsbattle.tournament.scheduler.GroupKnockoutScheduler.group(t,m));row.put("seriesId",m.seriesId);row.put("gameNumber",m.gameNumber);row.put("round",m.round);row.put("scheduledAt",m.scheduledAt);row.put("startedAt",m.startedAt);row.put("finishedAt",m.finishedAt);row.put("remainingA",m.remainingA);row.put("remainingB",m.remainingB);return row;}).toList());
        int pending=0,running=0,finished=0,failed=0;
        for(var m:t.matches) switch(m.status){case PENDING->pending++;case RUNNING->running++;case FINISHED->finished++;case FAILED->failed++;}
        int total=t.format==TournamentFormat.GROUP_KNOCKOUT?t.matches.size():t.bestOf>1?t.matches.size():(t.format!=TournamentFormat.ROUND_ROBIN)&&!t.bracket.isEmpty()?(int)t.bracket.stream().filter(node->node.matchId()!=null&&(node.resolution()==null||t.matches.stream().anyMatch(m->m.id.equals(node.matchId())))).count():t.matches.size();
        data.put("progress",Map.of("total",total,"pending",pending+total-t.matches.size(),"running",running,"finished",finished,"failed",failed,"completed",finished+failed));
        var ranked=ranking.rank(t); data.put("ranking",ranked);data.put("swissStandings",com.rpsbattle.tournament.scheduler.SwissScheduler.standings(t,ranked));
        String championId=(t.format!=TournamentFormat.ROUND_ROBIN)&&!t.bracket.isEmpty()?t.bracket.getLast().winnerParticipantId():null;
        data.put("champion",t.status!=TournamentStatus.FINISHED||!t.operations.reviews().isEmpty()||t.format==TournamentFormat.TEAM_ROUND_ROBIN?null:Set.of(TournamentFormat.GROUP_KNOCKOUT,TournamentFormat.SINGLE_ELIMINATION).contains(t.format)?ranked.stream().filter(r->r.participantId().equals(championId)&&t.operations.active(r.participantId())).findFirst().orElse(null):finished>0&&!ranked.isEmpty()?ranked.stream().filter(r->t.operations.active(r.participantId())&&(r.wins()>0||r.draws()>0)).findFirst().orElse(null):null);
        return data;
    } }
}
