package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.TournamentBotPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;
@RestController @RequestMapping("/api/tournaments/{id}/bot-policy")
public class TournamentBotPolicyController {
 private final TournamentService tournaments;private final OwnedTournamentService owned;
 public TournamentBotPolicyController(TournamentService tournaments,OwnedTournamentService owned){this.tournaments=tournaments;this.owned=owned;}
 @GetMapping public ResponseEntity<?> get(@PathVariable String id){var t=tournaments.find(id);synchronized(t){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("tournamentId",t.id,"revision",t.revision,"policy",t.botPolicy));}}
 @PutMapping public ResponseEntity<?> update(Authentication auth,@PathVariable String id,@RequestBody Update request){owned.configureBotPolicy(auth,id,request.revision(),request.policy());return get(id);}
 public record Update(Long revision,TournamentBotPolicy policy){}
}
