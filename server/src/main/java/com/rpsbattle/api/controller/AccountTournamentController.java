package com.rpsbattle.api.controller;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/account/tournaments")
public class AccountTournamentController {
    private final AccountAuthorizationService accounts;private final TournamentRegistry registry;private final TournamentService tournaments;private final OwnedTournamentService owned;
    public AccountTournamentController(AccountAuthorizationService accounts,TournamentRegistry registry,TournamentService tournaments,OwnedTournamentService owned){this.accounts=accounts;this.registry=registry;this.tournaments=tournaments;this.owned=owned;}
    @GetMapping public Object list(Authentication auth,@RequestParam(defaultValue="all") String filter){
        var user=accounts.requireUser(auth).id().value();if(!Set.of("all","created","participated","finished","won").contains(filter))throw new AuthException("INVALID_HISTORY_QUERY",400);owned.flush();
        var result=new ArrayList<Map<String,Object>>();
        for(var t:registry.findAll().stream().sorted(Comparator.comparing((Tournament t)->t.createdAt).reversed()).toList())synchronized(t){
            boolean creator=user.equals(t.ownerUserId),participant=t.registrations.stream().anyMatch(r->r.userId().equals(user));
            boolean won=t.status==TournamentStatus.FINISHED&&tournaments.ranking(t).stream().findFirst().filter(r->t.operations.active(r.participantId())&&t.participants.stream().anyMatch(p->p.participantId().equals(r.participantId())&&user.equals(p.ownerUserId()))).isPresent()&&tournaments.view(t).get("champion")!=null;
            if((creator||participant)&&switch(filter){case "created"->creator;case "participated"->participant;case "finished"->t.status==TournamentStatus.FINISHED;case "won"->won;default->true;})result.add(tournaments.view(t));
        }return result;
    }
}
