package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.cleanup.TournamentCleanupService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class TournamentCleanupController {
 private final TournamentCleanupService cleanup;
 public TournamentCleanupController(TournamentCleanupService cleanup){this.cleanup=cleanup;}
 @GetMapping("/api/admin/tournament-cleanup") public ResponseEntity<?> list(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items",cleanup.list(auth)));}
 @PostMapping("/api/admin/tournament-cleanup/{id}/restore") public Map<String,Boolean> restore(Authentication auth,@PathVariable String id,@RequestBody Action body){cleanup.restore(auth,id,body.reason());return Map.of("ok",true);}
 @PostMapping("/api/tournaments/{id}/keep") public Map<String,Boolean> keep(Authentication auth,@PathVariable String id,@RequestBody Action body){cleanup.keep(auth,id,body.revision(),body.reason());return Map.of("ok",true);}
 public record Action(Long revision,String reason){}
}
