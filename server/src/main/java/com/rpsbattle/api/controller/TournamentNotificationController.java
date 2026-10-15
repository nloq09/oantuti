package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.TournamentNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
public class TournamentNotificationController {
 private final TournamentNotificationService service;public TournamentNotificationController(TournamentNotificationService service){this.service=service;}
 @GetMapping("/api/tournaments/{id}/notifications") public ResponseEntity<?> list(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(auth,id));}
 @PostMapping("/api/tournaments/{id}/notifications/read") public ResponseEntity<?> read(Authentication auth,@PathVariable String id,@RequestBody Read body){service.read(auth,id,body.through());return list(auth,id);}
 @GetMapping("/api/account/tournament-notifications") public ResponseEntity<?> feed(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.feed(auth));}
 public record Read(long through){}
}
