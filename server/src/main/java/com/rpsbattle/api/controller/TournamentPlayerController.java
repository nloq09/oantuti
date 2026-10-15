package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentPlayerController {
    private final TournamentDiscoveryService discovery;private final ParticipantTournamentStateService participants;private final OwnedTournamentService owned;private final TournamentReadyService readiness;
    public TournamentPlayerController(TournamentDiscoveryService discovery,ParticipantTournamentStateService participants,OwnedTournamentService owned,TournamentReadyService readiness){this.discovery=discovery;this.participants=participants;this.owned=owned;this.readiness=readiness;}
    @org.springframework.beans.factory.annotation.Autowired private TournamentMatchContextService context;
    @GetMapping("/{id}/match-context") public ResponseEntity<?> context(@PathVariable String id,@RequestParam String onlineMatchId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(context.context(id,onlineMatchId));}
    @PostMapping("/{id}/ready") public ResponseEntity<?> ready(Authentication auth,@PathVariable String id,@RequestBody Ready body){readiness.ready(auth,id,body.matchId(),body.windowId());return me(auth,id);}
    @PostMapping("/{id}/entry") public ResponseEntity<?> entry(Authentication auth,@PathVariable String id,@RequestBody Ready body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readiness.entry(auth,id,body.matchId()));}
    public record Ready(String matchId,String windowId){}
    @PostMapping("/{id}/timing") public ResponseEntity<?> timing(Authentication auth,@PathVariable String id,@RequestBody Timing body){owned.configureTiming(auth,id,body.revision(),new com.rpsbattle.tournament.domain.TournamentTiming(body.registrationDeadline(),body.checkInDeadline(),body.startTime()));return ResponseEntity.noContent().build();}
    public record Timing(Long revision,java.time.Instant registrationDeadline,java.time.Instant checkInDeadline,java.time.Instant startTime){}
    @GetMapping("/{id}/me") public ResponseEntity<?> me(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(participants.me(auth,id));}
    @GetMapping("/discover") public ResponseEntity<?> discover(Authentication auth,@RequestParam(defaultValue="live") String category,@RequestParam(required=false)String q,@RequestParam(required=false)String format,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="12")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discovery.discover(auth,category,q,format,page,size));}
}
