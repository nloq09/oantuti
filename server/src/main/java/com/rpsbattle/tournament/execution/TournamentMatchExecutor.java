package com.rpsbattle.tournament.execution;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.bot.BotAgent;
import com.rpsbattle.bot.upload.BotUploadService;
import com.rpsbattle.online.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component
public class TournamentMatchExecutor {
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
            a=bots.agent(entry.participantA.botId(),entry.participantA.botToken());
            b=bots.agent(entry.participantB.botId(),entry.participantB.botToken());
            room=online.create(MatchMode.HUMAN_VS_HUMAN,entry.participantA.seed()^entry.participantB.seed());
            room.setBotDelayMillis(botDelayMillis);
            synchronized(room) {
                room.attachBot(OnlineMatch.P1,a,entry.participantA.displayName());
                var other=room.join(); room.attachBot(other.id(),b,entry.participantB.displayName());
                if(identities!=null) {
                    identities.bindTournamentBot(room,OnlineMatch.P1,entry.participantA.botId());
                    identities.bindTournamentBot(room,other.id(),entry.participantB.botId());
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
