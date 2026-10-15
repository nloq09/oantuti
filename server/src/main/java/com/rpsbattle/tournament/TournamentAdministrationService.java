package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.scheduler.SingleEliminationScheduler;
import com.rpsbattle.online.DemoRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class TournamentAdministrationService {
    public void assignReview(Authentication auth,String id,Long revision,String matchId,String explanation){
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(t.operations.reviews().containsKey(matchId),"MATCH_NOT_UNDER_REVIEW");
            TournamentAudit.append(t,"MATCH_REVIEW_ASSIGNED",owned.actor(auth),matchId,why,null,owned.actor(auth).toString());tournaments.changed(t);
        });
    }
    public void rejectReview(Authentication auth,String id,Long revision,String matchId,String explanation){
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(t.operations.reviews().containsKey(matchId),"MATCH_NOT_UNDER_REVIEW");t.operations=t.operations.review(matchId,null);
            TournamentAudit.append(t,"MATCH_REVIEW_REJECTED",owned.actor(auth),matchId,why,null,TournamentAudit.result(match(t,matchId)));tournaments.changed(t);
        });
    }
    public void disqualifyReview(Authentication auth,String id,Long revision,String matchId,String participantId,String explanation){
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(t.status==TournamentStatus.PAUSED||t.status==TournamentStatus.RECOVERY_REQUIRED,"PAUSE_REQUIRED");
            require(t.operations.reviews().containsKey(matchId),"MATCH_NOT_UNDER_REVIEW");var m=match(t,matchId);
            require(m.participantA.participantId().equals(participantId)||m.participantB.participantId().equals(participantId),"INVALID_PARTICIPANT_STATUS");
            require(t.operations.active(participantId),"PARTICIPANT_INACTIVE");
            require(t.matches.stream().noneMatch(game->game.status==TournamentMatch.Status.RUNNING&&(game.participantA.participantId().equals(participantId)||game.participantB.participantId().equals(participantId))),"PARTICIPANT_BUSY");
            t.operations=t.operations.participant(participantId,ParticipantStatus.DISQUALIFIED).review(matchId,null);
            TournamentAudit.append(t,"PARTICIPANT_DISQUALIFIED",owned.actor(auth),participantId,why,"ACTIVE","DISQUALIFIED");
            settleForfeits(t);if(t.format==TournamentFormat.SINGLE_ELIMINATION)t.matches.addAll(SingleEliminationScheduler.advance(t));
            TournamentAudit.append(t,"MATCH_REVIEW_CLOSED",owned.actor(auth),matchId,why,null,"DISQUALIFIED:"+participantId);tournaments.changed(t);
        });
    }
    private final OwnedTournamentService owned;
    private final TournamentService tournaments;
    public TournamentAdministrationService(OwnedTournamentService owned,TournamentService tournaments){this.owned=owned;this.tournaments=tournaments;}
    private static void require(boolean valid,String code){if(!valid)throw new DemoRequestException(code);}
    private static String reason(String value){require(value!=null&&!value.isBlank()&&value.length()<=500,"REASON_REQUIRED");return value.trim();}
    private static TournamentMatch match(Tournament t,String id){return t.matches.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow(()->new DemoRequestException("TOURNAMENT_MATCH_NOT_FOUND"));}
    public void control(Authentication auth,String id,Long revision,String action,String reason) {
        var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            var before=t.status;int beforeRound=t.operations.releasedRound();
            switch(action) {
                case "pause" -> {t.readyWindows.clear();TournamentStateMachine.transition(t,TournamentStatus.PAUSED,Instant.now());}
                case "resume" -> {
                    require(t.status==TournamentStatus.PAUSED||t.status==TournamentStatus.RECOVERY_REQUIRED,"TOURNAMENT_LOCKED");
                    require(t.operations.reviews().isEmpty()&&t.matches.stream().noneMatch(m->m.status==TournamentMatch.Status.FAILED),"MATCH_REVIEW_REQUIRED");
                    t.readyWindows.clear();
                    owned.verifyArtifacts(t);TournamentStateMachine.transition(t,TournamentStatus.RUNNING,Instant.now());
                }
                case "next-round" -> {
                    require(t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED,"TOURNAMENT_LOCKED");
                    require(!t.operations.autoAdvanceRounds()&&t.operations.releasedRound()<t.rounds.size(),"NO_NEXT_ROUND");
                    require(t.matches.stream().filter(m->m.round<=t.operations.releasedRound()).allMatch(m->m.status==TournamentMatch.Status.FINISHED)&&t.operations.reviews().isEmpty(),"ROUND_NOT_FINISHED");
                    t.operations=t.operations.rounds(false,t.operations.releasedRound()+1);
                }
                case "finish" -> {
                    reason(reason);require(t.status==TournamentStatus.RUNNING||t.status==TournamentStatus.PAUSED||t.status==TournamentStatus.RECOVERY_REQUIRED,"TOURNAMENT_LOCKED");
                    require(t.operations.reviews().isEmpty()&&t.matches.stream().noneMatch(m->m.status==TournamentMatch.Status.PENDING||m.status==TournamentMatch.Status.RUNNING),"MATCH_REVIEW_REQUIRED");
                    require(t.format!=TournamentFormat.SINGLE_ELIMINATION||SingleEliminationScheduler.complete(t),"BRACKET_NOT_FINISHED");
                    TournamentStateMachine.transition(t,TournamentStatus.FINISHED,Instant.now());
                }
                default -> throw new DemoRequestException("INVALID_TOURNAMENT_ACTION");
            }
            String event=switch(action){case "pause"->"TOURNAMENT_PAUSED";case "resume"->"TOURNAMENT_RESUMED";case "next-round"->"ROUND_RELEASED";default->"TOURNAMENT_FINISHED";};
            TournamentAudit.append(t,event,owned.actor(auth),action.equals("next-round")?Integer.toString(t.operations.releasedRound()):id,reason,action.equals("next-round")?Integer.toString(beforeRound):before.name(),action.equals("next-round")?Integer.toString(t.operations.releasedRound()):t.status.name());tournaments.changed(t);
        });
    }
    public void roundPolicy(Authentication auth,String id,Long revision,boolean auto) {
        var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(List.of(TournamentStatus.DRAFT,TournamentStatus.REGISTRATION,TournamentStatus.CHECK_IN).contains(t.status),"TOURNAMENT_LOCKED");
            t.operations=t.operations.rounds(auto,1);TournamentAudit.append(t,"ROUND_POLICY_UPDATED",owned.actor(auth),id,null,null,Boolean.toString(auto));tournaments.changed(t);
        });
    }
    public void participant(Authentication auth,String id,Long revision,String participantId,ParticipantStatus status,String explanation) {
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(t.status==TournamentStatus.PAUSED||t.status==TournamentStatus.RECOVERY_REQUIRED,"PAUSE_REQUIRED");
            require(status==ParticipantStatus.WITHDRAWN||status==ParticipantStatus.DISQUALIFIED,"INVALID_PARTICIPANT_STATUS");
            require(t.participants.stream().anyMatch(p->p.participantId().equals(participantId)),"PARTICIPANT_NOT_FOUND");
            require(t.operations.active(participantId),"PARTICIPANT_INACTIVE");
            require(t.matches.stream().noneMatch(m->m.status==TournamentMatch.Status.RUNNING&&(m.participantA.participantId().equals(participantId)||m.participantB.participantId().equals(participantId))),"PARTICIPANT_BUSY");
            t.operations=t.operations.participant(participantId,status);TournamentAudit.append(t,"PARTICIPANT_"+status,owned.actor(auth),participantId,why,"ACTIVE",status.name());
            settleForfeits(t);if(t.format==TournamentFormat.SINGLE_ELIMINATION)t.matches.addAll(SingleEliminationScheduler.advance(t));tournaments.changed(t);
        });
    }
    /** Only unplayed games are adjudicated; completed match history is retained. */
    public static boolean settleForfeits(Tournament t) {
        boolean changed=false;
        for(var m:t.matches)if(m.status==TournamentMatch.Status.PENDING) {
            boolean a=t.operations.active(m.participantA.participantId()),b=t.operations.active(m.participantB.participantId());if(a&&b)continue;
            m.status=TournamentMatch.Status.FINISHED;m.winnerParticipantId=a?m.participantA.participantId():b?m.participantB.participantId():null;
            m.result=m.winnerParticipantId==null?"DRAW":"WIN";m.decisionReason=a||b?"FORFEIT":"DOUBLE_FORFEIT";m.finishedAt=Instant.now();m.remainingA=0;m.remainingB=0;
            TournamentAudit.append(t,"MATCH_FINISHED",null,m.id,m.decisionReason,"PENDING",TournamentAudit.result(m));changed=true;
        }return changed;
    }
    public void review(Authentication auth,String id,Long revision,String matchId,String explanation,boolean keep) {
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            var m=match(t,matchId);require(m.status==TournamentMatch.Status.FINISHED||m.status==TournamentMatch.Status.FAILED,"MATCH_NOT_REVIEWABLE");
            require(t.status!=TournamentStatus.CANCELLED,"TOURNAMENT_LOCKED");
            if(keep)require(t.operations.reviews().containsKey(matchId),"MATCH_NOT_UNDER_REVIEW");
            if(t.status==TournamentStatus.RUNNING)TournamentStateMachine.transition(t,TournamentStatus.PAUSED,Instant.now());
            t.operations=t.operations.review(matchId,keep?null:why);TournamentAudit.append(t,keep?"MATCH_REVIEW_CLOSED":"MATCH_REVIEW_REQUESTED",owned.actor(auth),matchId,why,null,TournamentAudit.result(m));tournaments.changed(t);
        });
    }
    public void retry(Authentication auth,String id,Long revision,String matchId,String explanation) {
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(t.status==TournamentStatus.PAUSED||t.status==TournamentStatus.RECOVERY_REQUIRED,"PAUSE_REQUIRED");var m=match(t,matchId);
            require(m.status==TournamentMatch.Status.FAILED&&!"CANCELLED".equals(m.failure),"MATCH_NOT_RETRYABLE");String before=TournamentAudit.result(m);
            t.readyWindows.remove(m.id);m.status=TournamentMatch.Status.PENDING;m.onlineMatchId=null;m.historyMatchId=null;m.winnerParticipantId=null;m.result=null;m.failure=null;m.startedAt=null;m.finishedAt=null;m.attempt++;m.scheduledAt=Instant.now();
            m.moveNanosA=0;m.moveNanosB=0;m.moveCountA=0;m.moveCountB=0;m.remainingA=0;m.remainingB=0;m.decisionReason=null;
            t.operations=t.operations.review(matchId,null);TournamentAudit.append(t,"MATCH_RETRIED",owned.actor(auth),matchId,why,before,TournamentAudit.result(m));tournaments.changed(t);
        });
    }
    public void override(Authentication auth,String id,Long revision,String matchId,String winner,String explanation) {
        String why=reason(explanation);var t=tournaments.find(id);owned.managedMutation(auth,t,revision,()->{
            require(List.of(TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.FINISHED).contains(t.status),"PAUSE_REQUIRED");var m=match(t,matchId);
            require(t.operations.reviews().containsKey(matchId)&&m.status==TournamentMatch.Status.FINISHED,"MATCH_NOT_UNDER_REVIEW");
            require(winner==null||((winner.equals(m.participantA.participantId())||winner.equals(m.participantB.participantId()))&&t.operations.active(winner)),"INVALID_WINNER");
            if(t.format==TournamentFormat.SINGLE_ELIMINATION) {
                require(t.matches.stream().filter(other->other.round>m.round).allMatch(other->other.status==TournamentMatch.Status.PENDING),"DOWNSTREAM_MATCH_STARTED");
                t.matches.removeIf(other->other.round>m.round);
                for(int i=0;i<t.bracket.size();i++){var node=t.bracket.get(i);if(node.round()>m.round||m.id.equals(node.matchId()))t.bracket.set(i,node.resolved(node.sourceA()==null?node.slotA():null,node.sourceB()==null?node.slotB():null,null,null));}
            }
            String before=TournamentAudit.result(m);m.winnerParticipantId=winner;m.result=winner==null?"DRAW":"WIN";m.decisionReason="RESULT_OVERRIDE";
            t.operations=t.operations.review(matchId,null);if(t.format==TournamentFormat.SINGLE_ELIMINATION)t.matches.addAll(SingleEliminationScheduler.advance(t));
            TournamentAudit.append(t,"RESULT_OVERRIDDEN",owned.actor(auth),matchId,why,before,TournamentAudit.result(m));tournaments.changed(t);
        });
    }
}
