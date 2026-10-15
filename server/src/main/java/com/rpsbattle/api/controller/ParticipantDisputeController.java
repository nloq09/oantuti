package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.ParticipantDisputeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments/{id}/disputes")
public class ParticipantDisputeController {
 private final ParticipantDisputeService service;public ParticipantDisputeController(ParticipantDisputeService service){this.service=service;}
 @GetMapping public ResponseEntity<?> list(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(auth,id));}
 @PostMapping public ResponseEntity<?> report(Authentication auth,@PathVariable String id,@RequestBody Report body){service.report(auth,id,body.matchId(),body.reason(),body.description());return list(auth,id);}
 public record Report(String matchId,String reason,String description){}
}
