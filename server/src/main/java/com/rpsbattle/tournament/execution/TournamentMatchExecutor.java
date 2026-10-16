package com.rpsbattle.tournament.execution;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.bot.BotAgent;
import com.rpsbattle.bot.upload.BotUploadService;
import com.rpsbattle.online.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component
public class TournamentMatchExecutor {
    private com.rpsbattle.tournament.coding.SubmissionStore submissions;
    private com.rpsbattle.tournament.coding.WorkspaceSandbox sandbox;
    @org.springframework.beans.factory.annotation.Autowired public void submissions(com.rpsbattle.tournament.coding.SubmissionStore store,com.rpsbattle.tournament.coding.WorkspaceSandbox sandbox){this.submissions=store;this.sandbox=sandbox;}
    private BotAgent agent(TournamentMatch entry,TournamentParticipant p){if(submissions!=null){var work=submissions.execution(entry.tournamentId,p.participantId(),p.botId());if(work!=null)return new BotAgent(sandbox.open(work),java.time.Duration.ofSeconds(2));}return p.artifactHash()==null?bots.agent(p.botId(),p.botToken()):bots.lockedAgent(p.botId(),p.botToken(),p.artifactHash());}
    private final OnlineMatchService online;
    private final BotUploadService bots;
    private final long botDelayMillis;
    private final com.rpsbattle.history.MatchSeatService identities;
    public TournamentMatchExecutor(OnlineMatchService online,BotUploadService bots,long delay){this(online,bots,delay,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public TournamentMatchExecutor(OnlineMatchService online,BotUploadService bots,@org.springframework.beans.factory.annotation.Value("${rps.tournament.bot-delay-millis:600}") long delay,com.rpsbattle.history.MatchSeatService identities){this.online=online;this.bots=bots;this.botDelayMillis=delay;this.identities=identities;}
    public OnlineMatch start(TournamentMatch entry) {
        BotAgent a=null,b=null; OnlineMatch room=null;
        try {
            if(entry.participantA.botId()!=null)a=agent(entry,entry.participantA);
            if(entry.participantB.botId()!=null)b=agent(entry,entry.participantB);
            room=online.create(MatchMode.HUMAN_VS_HUMAN,entry.participantA.seed()^entry.participantB.seed());
            room.setBotDelayMillis(botDelayMillis);
            entry.historyMatchId=room.archiveId();
            synchronized(room) {
                if(a!=null)room.attachBot(OnlineMatch.P1,a,entry.participantA.displayName());
                var other=room.join(); if(b!=null)room.attachBot(other.id(),b,entry.participantB.displayName());
                if(identities!=null) {
                    if(entry.participantA.botId()==null)room.bindSeat(OnlineMatch.P1,entry.participantA.ownerUserId(),null);else identities.bindTournamentBot(room,OnlineMatch.P1,entry.participantA.botId());
                    if(entry.participantB.botId()==null)room.bindSeat(other.id(),entry.participantB.ownerUserId(),null);else identities.bindTournamentBot(room,other.id(),entry.participantB.botId());
                }
                online.publish(room,room.prepareBots());
            }
            entry.onlineMatchId=room.id();entry.status=TournamentMatch.Status.RUNNING;entry.startedAt=Instant.now();return room;
        } catch(RuntimeException failure) {
            if(room!=null) online.release(room.id());
            if(a!=null)a.close(); if(b!=null)b.close();throw failure;
        }
    }
}
