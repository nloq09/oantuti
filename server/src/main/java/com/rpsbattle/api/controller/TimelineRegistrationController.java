package com.rpsbattle.api.controller;

import com.rpsbattle.tournament.timeline.TimelineRegistrationService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/tournaments/{id}")
public class TimelineRegistrationController {
    private final TimelineRegistrationService service;
    public TimelineRegistrationController(TimelineRegistrationService service){this.service=service;}
    private ResponseEntity<?> response(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(body);}
    @GetMapping("/participant-flow") public ResponseEntity<?> flow(Authentication auth,@PathVariable String id){return response(service.read(auth,id));}
    @PostMapping("/participant-registration") public ResponseEntity<?> register(Authentication auth,@PathVariable String id,@RequestBody TimelineRegistrationService.Signup request){return response(service.register(auth,id,request));}
    @DeleteMapping("/participant-registration") public ResponseEntity<?> withdraw(Authentication auth,@PathVariable String id){return response(service.withdraw(auth,id));}
}
