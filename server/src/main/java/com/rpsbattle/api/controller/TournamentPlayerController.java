package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentPlayerController {
    private final TournamentDiscoveryService discovery;private final ParticipantTournamentStateService participants;
    public TournamentPlayerController(TournamentDiscoveryService discovery,ParticipantTournamentStateService participants){this.discovery=discovery;this.participants=participants;}
    @GetMapping("/{id}/me") public ResponseEntity<?> me(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(participants.me(auth,id));}
    @GetMapping("/discover") public ResponseEntity<?> discover(Authentication auth,@RequestParam(defaultValue="live") String category,@RequestParam(required=false)String q,@RequestParam(required=false)String format,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="12")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discovery.discover(auth,category,q,format,page,size));}
}
