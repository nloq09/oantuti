package com.rpsbattle.remote;

import com.rpsbattle.bot.*;
import com.rpsbattle.game.action.*;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.match.*;
import com.rpsbattle.online.*;
import com.rpsbattle.replay.*;
import com.rpsbattle.rules.online.OnlineRuleSet;
import java.time.*;

/** Uses the existing online placement and engine, with no server, sockets or UI loop. */
public final class HeadlessMatch {
    public record Result(String status,String winner,String loser,String reason,int turnCount,long durationMs,MatchLog replay) {}
    public Result run(ExecutionJob.Spec spec,BotProcess a,BotProcess b,long deadline) {
        long began=System.nanoTime();
        try(a;b) {
            a.start();b.start();check(deadline);
            var room=new OnlineMatch(spec.matchId().toString(),MatchMode.HUMAN_VS_HUMAN,spec.seed(),Clock.systemUTC(),Duration.ofMillis(spec.turnTimeoutMs()));
            var agentA=new BotAgent(a,Duration.ofMillis(spec.turnTimeoutMs()));var agentB=new BotAgent(b,Duration.ofMillis(spec.turnTimeoutMs()));
            room.attachBot(OnlineMatch.P1,agentA,"Bot A");room.join();room.attachBot(OnlineMatch.P2,agentB,"Bot B");room.prepareBots();
            var rules=new OnlineRuleSet(OnlineMatch.P1,OnlineMatch.P2,spec.maxTurns());
            var match=new Match(room.state().withPhase(com.rpsbattle.game.state.GamePhase.READY),rules,agentA,agentB,spec.seed());match.start();
            var legal=new com.rpsbattle.game.engine.LegalActionGenerator(rules);
            String fault=null;
            while(match.status()==MatchStatus.RUNNING) {
                check(deadline);var player=match.state().currentPlayerId();var actions=legal.getLegalActions(match.state(),player);
                if(actions.isEmpty())actions=java.util.List.of(PassAction.of(player));
                try {
                    var action=(player.equals(OnlineMatch.P1)?agentA:agentB).chooseAction(new PlayerView(match.state(),player),actions);
                    if(!actions.contains(action))throw new BotProtocolException("ILLEGAL_ACTION");
                    check(deadline);match.submitAction(player,action);
                }catch(BotInfrastructureException infrastructure){throw infrastructure;}
                catch(BotProtocolException|com.rpsbattle.game.engine.InvalidActionException botFailure){
                    check(deadline);fault="BOT_FAILED";match.submitAction(player,SurrenderAction.of(player));
                }
            }
            check(deadline);var state=match.state();var result=state.result().orElseThrow();String winner=result.winner().map(p->p.value()).orElse(null);
            var log=new MatchLog(match.matchId(),rules.name(),spec.seed(),match.initialState(),match.events(),result);
            new ReplayService().replay(log); // A corrupt/incomplete event stream is never an uploadable result.
            return new Result("FINISHED",winner,winner==null?null:winner.equals("P1")?"P2":"P1",fault==null?"ENGINE_RESULT":fault,state.turnNumber(),(System.nanoTime()-began)/1000000,log);
        }
    }
    static void check(long deadline){if(Thread.currentThread().isInterrupted()||System.nanoTime()>=deadline)throw new IllegalStateException("MATCH_HARD_TIMEOUT");}
}
