package com.rpsbattle.tournament;

import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.scheduler.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Public, bounded projections. Personal registration and credentials never enter these rows. */
@Service
public class PlayerTournamentPageService {
    private final TournamentService tournaments;
    private final UserRepository users;
    public PlayerTournamentPageService(TournamentService tournaments,UserRepository users){this.tournaments=tournaments;this.users=users;}
    public Map<String,Object> page(String id,String section,int page,int size){
        if(page<0||page>100000||size<1||size>50)throw new AuthException("INVALID_PAGE",400);
        if(!Set.of("participants","ranking").contains(section))throw new AuthException("INVALID_FILTER",400);
        var t=tournaments.find(id);synchronized(t){
            long offset=(long)page*size;List<?> items;long total;
            if(section.equals("participants")){
                total=t.participants.size();var selected=t.participants.stream().skip(offset).limit(size).toList();
                var ids=selected.stream().map(TournamentParticipant::ownerUserId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
                var names=ids.isEmpty()?Map.<UUID,String>of():users.displayNames(ids);var rows=new ArrayList<Map<String,Object>>();
                for(var participant:selected){var row=new LinkedHashMap<String,Object>();row.put("participantId",participant.participantId());row.put("displayName",participant.displayName());row.put("controllerType",participant.controllerType());row.put("ownerName",participant.ownerUserId()==null?"Người chơi":names.getOrDefault(participant.ownerUserId(),"Người chơi"));row.put("status",t.operations.participants().containsKey(participant.participantId())?t.operations.participants().get(participant.participantId()).name():"ACTIVE");rows.add(row);}items=rows;
            }else{
                var ranked=tournaments.ranking(t);total=ranked.size();var rows=new ArrayList<Map<String,Object>>();
                for(var rank:ranked.stream().skip(offset).limit(size).toList()){var row=new LinkedHashMap<String,Object>();row.put("participantId",rank.participantId());row.put("rank",rank.rank());row.put("displayName",rank.displayName());row.put("points",rank.points());row.put("played",rank.played());row.put("wins",rank.wins());row.put("draws",rank.draws());row.put("losses",rank.losses());if(t.swiss!=null){row.put("buchholz",SwissScheduler.buchholz(t,rank.participantId()));row.put("byes",t.rounds.stream().filter(round->round.byeParticipantIds().contains(rank.participantId())).map(TournamentRound::number).toList());}rows.add(row);}items=rows;
            }
            return Map.of("tournamentId",id,"revision",t.revision,"items",items,"total",total,"page",page,"size",size);
        }
    }
    public Map<String,Object> extra(String id,String section){
        var t=tournaments.find(id);synchronized(t){
            if(section.equals("groups"))return Map.of("tournamentId",id,"revision",t.revision,"groups",GroupKnockoutScheduler.view(t));
            // Team tournaments are capped at 32 entrants; retain the existing public team forms.
            if(section.equals("teams")&&t.format==TournamentFormat.TEAM_ROUND_ROBIN)return tournaments.summary(t);
            throw new AuthException("INVALID_FILTER",400);
        }
    }
}
