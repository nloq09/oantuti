package com.rpsbattle.tournament;
import com.rpsbattle.account.AccountAuthorizationService;import com.rpsbattle.tournament.domain.*;import org.springframework.security.core.Authentication;import org.springframework.stereotype.Service;import java.time.Instant;import java.util.*;
@Service public class ParticipantTournamentStateService {
 private final TournamentService tournaments;private final OwnedTournamentService owned;private final AccountAuthorizationService accounts;
 public ParticipantTournamentStateService(TournamentService tournaments,OwnedTournamentService owned,AccountAuthorizationService accounts){this.tournaments=tournaments;this.owned=owned;this.accounts=accounts;}
 public Map<String,Object> me(Authentication auth,String id){var actor=accounts.requireUser(auth);owned.flush();var t=tournaments.find(id);synchronized(t){var view=tournaments.view(t);var states=new ArrayList<Map<String,Object>>();
  for(var r:t.registrations)if(r.userId().equals(actor.id().value())){var row=new LinkedHashMap<String,Object>();String key=r.id().toString();var participant=t.participants.stream().filter(p->p.participantId().equals(key)).findFirst();
   var games=t.matches.stream().filter(m->m.participantA.participantId().equals(key)||m.participantB.participantId().equals(key)).toList();var running=games.stream().filter(m->m.status==TournamentMatch.Status.RUNNING).findFirst();var next=games.stream().filter(m->m.status==TournamentMatch.Status.PENDING).min(Comparator.comparingInt(m->m.round));
   String status=switch(r.status()){case PENDING->"PENDING_APPROVAL";case REJECTED->"REJECTED";case CANCELLED->"WITHDRAWN";case NOT_CHECKED_IN->"MISSED_CHECK_IN";case APPROVED->t.checkInRequired&&t.status==TournamentStatus.CHECK_IN?"CHECK_IN_REQUIRED":"APPROVED";case CHECKED_IN->"CHECKED_IN";};
   if(participant.isPresent()&&t.operations.active(key)&&List.of(TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.FINISHED).contains(t.status)){
    status=running.isPresent()?"PLAYING":next.isPresent()?"WAITING_MATCH":"WAITING_ROUND";
    if(t.format==TournamentFormat.SINGLE_ELIMINATION&&games.stream().anyMatch(m->m.status==TournamentMatch.Status.FINISHED&&!key.equals(m.winnerParticipantId)))status="ELIMINATED";
    if(t.status==TournamentStatus.FINISHED){var champion=(com.rpsbattle.tournament.ranking.RankingEntry)view.get("champion");status=champion!=null&&key.equals(champion.participantId())?"CHAMPION":status.equals("ELIMINATED")?status:"FINISHED";}
   }
   if(t.operations.participants().containsKey(key))status=t.operations.participants().get(key).name();if(t.status==TournamentStatus.CANCELLED)status="TOURNAMENT_CANCELLED";
   row.put("registration",r);row.put("participantId",participant.map(TournamentParticipant::participantId).orElse(null));row.put("status",status);row.put("nextMatch",next.map(m->match(m,key)).orElse(null));row.put("currentMatch",running.map(m->match(m,key)).orElse(null));row.put("deadline",null);
   row.put("rank",tournaments.ranking(t).stream().filter(rank->rank.participantId().equals(key)).findFirst().orElse(null));states.add(row);
  }
  return Map.of("tournamentId",id,"tournamentStatus",t.status,"revision",t.revision,"serverTime",Instant.now(),"states",states);
 }}
 private Map<String,Object> match(TournamentMatch m,String participant){var result=new LinkedHashMap<String,Object>();result.put("id",m.id);result.put("round",m.round);result.put("opponent",m.participantA.participantId().equals(participant)?m.participantB.displayName():m.participantA.displayName());result.put("scheduledAt",m.scheduledAt);result.put("onlineMatchId",m.onlineMatchId);result.put("historyMatchId",m.historyMatchId);return result;}
}
