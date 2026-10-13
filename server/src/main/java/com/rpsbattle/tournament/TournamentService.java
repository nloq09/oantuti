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
    private final TournamentRegistry registry;
    private final BotUploadService bots;
    private final TournamentScheduler scheduler;
    private final RankingService ranking;
    private final List<Consumer<Tournament>> listeners=new CopyOnWriteArrayList<>();
    public TournamentService(TournamentRegistry registry,BotUploadService bots,TournamentScheduler scheduler,RankingService ranking) {
        this.registry=registry; this.bots=bots; this.scheduler=scheduler; this.ranking=ranking;
    }
    public Tournament find(String id) { return registry.findById(id).orElseThrow(()->new DemoRequestException("TOURNAMENT_NOT_FOUND")); }
    public Tournament create(String name,TournamentFormat format) { return registry.create(name,format); }
    public void authorizeOwner(String id, String token) {
        var t=find(id);
        if (token == null || !t.ownerToken.equals(token)) throw new DemoRequestException("TOURNAMENT_FORBIDDEN");
    }
    public void removeAuthorized(String id, String participantId, String ownerToken, String botToken) {
        var t = find(id);
        synchronized (t) {
            draft(t);
            var participant = t.participants.stream().filter(p -> p.participantId().equals(participantId))
                    .findFirst().orElseThrow(() -> new DemoRequestException("PARTICIPANT_NOT_FOUND"));
            if (!t.ownerToken.equals(ownerToken)) {
                if (botToken == null || !participant.botToken().equals(botToken)) throw new DemoRequestException("TOURNAMENT_FORBIDDEN");
                bots.authorize(participant.botId(), botToken);
            }
            remove(id, participantId);
        }
    }
    private void draft(Tournament t) { if(t.status!=TournamentStatus.DRAFT) throw new DemoRequestException("TOURNAMENT_LOCKED"); }
    public void add(String id,String botId,String token,String name,Long seed) {
        var t=find(id);
        var bot=bots.authorize(botId,token);
        synchronized(t) {
            draft(t);
            if(t.participants.size()>=10) throw new DemoRequestException("PARTICIPANT_LIMIT");
            if(t.participants.stream().anyMatch(p->p.botId().equals(botId))) throw new DemoRequestException("DUPLICATE_PARTICIPANT");
            if(name!=null && (name.isBlank()||name.length()>60)) throw new DemoRequestException("INVALID_BOT_NAME");
            t.participants.add(new TournamentParticipant(UUID.randomUUID().toString(),botId,token,name==null?bot.name():name.trim(),seed==null?UUID.randomUUID().getMostSignificantBits():seed));
            changed(t);
        }
    }
    public void remove(String id,String participantId) { var t=find(id); synchronized(t) { draft(t); if(!t.participants.removeIf(p->p.participantId().equals(participantId))) throw new DemoRequestException("PARTICIPANT_NOT_FOUND"); changed(t); } }
    public void start(String id) {
        var t=find(id); synchronized(t) {
            draft(t); if(t.participants.size()<2) throw new DemoRequestException("NOT_ENOUGH_BOTS");
            for(var p:t.participants) { var b=bots.authorize(p.botId(),p.botToken()); if(!bots.available(b.language())) throw new DemoRequestException("BOT_SANDBOX_UNAVAILABLE"); }
            t.status=TournamentStatus.READY;
            t.matches.addAll(scheduler.schedule(t)); t.status=TournamentStatus.RUNNING; t.startedAt=Instant.now(); changed(t);
        }
    }
    public void cancel(String id) { var t=find(id); synchronized(t) {
        if(t.status==TournamentStatus.FINISHED||t.status==TournamentStatus.CANCELLED) throw new DemoRequestException("TOURNAMENT_LOCKED");
        t.status=TournamentStatus.CANCELLED; t.finishedAt=Instant.now(); changed(t);
    } }
    public void listen(Consumer<Tournament> listener){listeners.add(listener);}
    public void changed(Tournament t){t.revision++;if(!t.deferNotifications) notifyChanged(t);}
    public void notifyChanged(Tournament t){for(var listener:listeners) {try {listener.accept(t);} catch(RuntimeException ignored){}}}
    public List<Map<String,Object>> list(){ return registry.findAll().stream().sorted(Comparator.comparing((Tournament t)->t.createdAt).reversed()).map(this::view).toList(); }
    public Map<String,Object> view(Tournament t) { synchronized(t) {
        var data=new LinkedHashMap<String,Object>();
        data.put("id",t.id);data.put("name",t.name);data.put("format",t.format);data.put("status",t.status);
        data.put("ownerUserId",t.ownerUserId);data.put("visibility","PUBLIC");
        data.put("createdAt",t.createdAt);data.put("startedAt",t.startedAt);data.put("finishedAt",t.finishedAt);data.put("revision",t.revision);
        data.put("participants",List.copyOf(t.participants));
        // Copy mutable matches while holding the monitor; never expose internal tokens or mutable objects.
        data.put("matches",t.matches.stream().map(m->{var row=new LinkedHashMap<String,Object>();
            row.put("id",m.id);row.put("participantA",m.participantA);row.put("participantB",m.participantB);row.put("onlineMatchId",m.onlineMatchId);row.put("historyMatchId",m.historyMatchId);
            row.put("status",m.status);row.put("winnerParticipantId",m.winnerParticipantId);row.put("result",m.result);row.put("failure",m.failure);
            row.put("startedAt",m.startedAt);row.put("finishedAt",m.finishedAt);row.put("remainingA",m.remainingA);row.put("remainingB",m.remainingB);return row;}).toList());
        int pending=0,running=0,finished=0,failed=0;
        for(var m:t.matches) switch(m.status){case PENDING->pending++;case RUNNING->running++;case FINISHED->finished++;case FAILED->failed++;}
        data.put("progress",Map.of("total",t.matches.size(),"pending",pending,"running",running,"finished",finished,"failed",failed,"completed",finished+failed));
        var ranked=ranking.rank(t); data.put("ranking",ranked);
        data.put("champion",t.status==TournamentStatus.FINISHED&&finished>0&&!ranked.isEmpty()?ranked.get(0):null);
        return data;
    } }
}
