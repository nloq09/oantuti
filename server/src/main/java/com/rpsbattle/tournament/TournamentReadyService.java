package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;import com.rpsbattle.account.AccountAuthorizationService;import com.rpsbattle.auth.AuthException;import com.rpsbattle.online.*;import org.springframework.security.core.Authentication;import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;import java.time.*;import java.util.*;
/** Admission before the existing worker; it neither creates rooms nor evaluates game actions. */
@Service public class TournamentReadyService {
 private final TournamentService tournaments;private final OwnedTournamentService owned;private final AccountAuthorizationService accounts;private final OnlineMatchService online;private final Clock clock;private final int seconds;
 public TournamentReadyService(TournamentService tournaments,OwnedTournamentService owned,AccountAuthorizationService accounts,OnlineMatchService online,Clock clock,@Value("${rps.tournament.ready-seconds:30}")int seconds){if(seconds<1||seconds>300)throw new IllegalArgumentException("Invalid ready window");this.tournaments=tournaments;this.owned=owned;this.accounts=accounts;this.online=online;this.clock=clock;this.seconds=seconds;}
 private boolean human(TournamentMatch m){return m.participantA.botId()==null||m.participantB.botId()==null;}
 public boolean confirmed(Tournament t,TournamentMatch m){if(!human(m))return true;var w=t.readyWindows.get(m.id);return w!=null&&w.confirmed().contains(m.participantA.participantId())&&w.confirmed().contains(m.participantB.participantId());}
 public boolean permit(Tournament t,TournamentMatch m){synchronized(t){
  if(!human(m))return true;if(t.status!=TournamentStatus.RUNNING||m.status!=TournamentMatch.Status.PENDING)return false;
  var window=t.readyWindows.get(m.id);if(window==null){
   long occupied=t.matches.stream().filter(game->game.status==TournamentMatch.Status.RUNNING||game.status==TournamentMatch.Status.PENDING&&t.readyWindows.containsKey(game.id)).count();if(occupied>=t.maxConcurrentMatches)return false;
   var automatic=new HashSet<String>();for(var p:List.of(m.participantA,m.participantB))if(p.botId()!=null)automatic.add(p.participantId());
   owned.systemMutation(t,()->{t.readyWindows.put(m.id,new MatchReadyWindow(UUID.randomUUID().toString(),clock.instant().plusSeconds(seconds),automatic));TournamentAudit.append(t,"MATCH_READY_REQUIRED",null,m.id,null,null,null);tournaments.changed(t);});return false;
  }
  if(confirmed(t,m))return true;
  if(!clock.instant().isBefore(window.deadline())){owned.systemMutation(t,()->{boolean a=window.confirmed().contains(m.participantA.participantId()),b=window.confirmed().contains(m.participantB.participantId());m.winnerParticipantId=a==b?null:a?m.participantA.participantId():m.participantB.participantId();m.status=TournamentMatch.Status.FINISHED;m.result=m.winnerParticipantId==null?"DRAW":"WIN";m.failure="NO_SHOW";m.decisionReason=a==b?"DOUBLE_FORFEIT":"FORFEIT";m.finishedAt=clock.instant();TournamentAudit.append(t,"MATCH_NO_SHOW",null,m.id,"READY_DEADLINE",null,TournamentAudit.result(m));tournaments.changed(t);});}return false;
 }}
 public void ready(Authentication auth,String id,String matchId,String windowId){var actor=accounts.requireUser(auth);var t=tournaments.find(id);synchronized(t){var m=find(t,matchId);var p=List.of(m.participantA,m.participantB).stream().filter(candidate->candidate.botId()==null&&actor.id().value().equals(candidate.ownerUserId())).findFirst().orElseThrow(()->new AuthException("TOURNAMENT_FORBIDDEN",403));var window=t.readyWindows.get(m.id);
  if(t.status!=TournamentStatus.RUNNING||window==null||!window.id().equals(windowId)||!t.operations.active(p.participantId()))throw new AuthException("READY_WINDOW_CHANGED",409);
  if(m.status!=TournamentMatch.Status.PENDING&&m.status!=TournamentMatch.Status.RUNNING)throw new AuthException("READY_WINDOW_CLOSED",409);
  if(window.confirmed().contains(p.participantId()))return;
  if(m.status!=TournamentMatch.Status.PENDING||!clock.instant().isBefore(window.deadline()))throw new AuthException("READY_WINDOW_CLOSED",409);
  owned.systemMutation(t,()->{t.readyWindows.put(m.id,window.confirm(p.participantId()));TournamentAudit.append(t,"MATCH_PARTICIPANT_READY",actor.id().value(),m.id,null,null,p.participantId());tournaments.changed(t);});
 }}
 public Map<String,Object> entry(Authentication auth,String id,String matchId){var actor=accounts.requireUser(auth);var t=tournaments.find(id);synchronized(t){var m=find(t,matchId);var mine=List.of(m.participantA,m.participantB).stream().filter(p->actor.id().value().equals(p.ownerUserId())&&t.operations.active(p.participantId())).toList();if(mine.isEmpty())throw new AuthException("TOURNAMENT_FORBIDDEN",403);
  if(!List.of(TournamentStatus.RUNNING,TournamentStatus.PAUSED).contains(t.status)||m.status!=TournamentMatch.Status.RUNNING||m.onlineMatchId==null)throw new AuthException("MATCH_NOT_ASSIGNED",409);
  OnlineMatch room;try{room=online.find(m.onlineMatchId);}catch(RuntimeException missing){throw new AuthException("MATCH_NOT_ASSIGNED",409);}synchronized(room){if(room.state().isFinished())throw new AuthException("MATCH_NOT_ASSIGNED",409);
   var human=mine.stream().filter(p->p.botId()==null).findFirst();if(human.isEmpty())return Map.of("role","SPECTATOR","matchId",room.id(),"tournamentId",id);
   var seatId=human.get().participantId().equals(m.participantA.participantId())?OnlineMatch.P1:OnlineMatch.P2;if(!actor.id().value().equals(room.seatIdentity(seatId).userId()))throw new AuthException("TOURNAMENT_FORBIDDEN",403);var seat=room.seats().stream().filter(s->s.id().equals(seatId)&&!s.bot()).findFirst().orElseThrow(()->new AuthException("TOURNAMENT_FORBIDDEN",403));
   return Map.of("role","PLAYER","matchId",room.id(),"historyId",room.archiveId(),"playerId",seat.id().value(),"token",seat.token(),"mode",room.mode().name(),"tournamentId",id,"tournamentMatchId",m.id);
  }
 }}
 private TournamentMatch find(Tournament t,String id){return t.matches.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow(()->new AuthException("TOURNAMENT_MATCH_NOT_FOUND",404));}
}
