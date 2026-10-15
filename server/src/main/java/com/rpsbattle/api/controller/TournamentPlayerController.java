package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentPlayerController {
    private final TournamentDiscoveryService discovery;private final ParticipantTournamentStateService participants;private final OwnedTournamentService owned;
    public TournamentPlayerController(TournamentDiscoveryService discovery,ParticipantTournamentStateService participants,OwnedTournamentService owned){this.discovery=discovery;this.participants=participants;this.owned=owned;}
    @PostMapping("/{id}/timing") public ResponseEntity<?> timing(Authentication auth,@PathVariable String id,@RequestBody Timing body){owned.configureTiming(auth,id,body.revision(),new com.rpsbattle.tournament.domain.TournamentTiming(body.registrationDeadline(),body.checkInDeadline(),body.startTime()));return ResponseEntity.noContent().build();}
    public record Timing(Long revision,java.time.Instant registrationDeadline,java.time.Instant checkInDeadline,java.time.Instant startTime){}
    @GetMapping("/{id}/me") public ResponseEntity<?> me(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(participants.me(auth,id));}
    @GetMapping("/discover") public ResponseEntity<?> discover(Authentication auth,@RequestParam(defaultValue="live") String category,@RequestParam(required=false)String q,@RequestParam(required=false)String format,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="12")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discovery.discover(auth,category,q,format,page,size));}
}
