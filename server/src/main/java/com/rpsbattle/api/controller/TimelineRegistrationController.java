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
    @GetMapping("/registration-summary") public ResponseEntity<?> summary(Authentication auth,@PathVariable String id){return response(service.summary(auth,id));}
    @PostMapping("/registration/close") public ResponseEntity<?> close(Authentication auth,@PathVariable String id,@RequestBody TimelineRegistrationService.LifecycleAction request){service.lifecycle(auth,id,request,false);return response(service.summary(auth,id));}
    @PostMapping("/roster/lock") public ResponseEntity<?> lock(Authentication auth,@PathVariable String id,@RequestBody TimelineRegistrationService.LifecycleAction request){service.lifecycle(auth,id,request,true);return response(service.summary(auth,id));}
    @PutMapping("/registration-policy") public ResponseEntity<?> policy(Authentication auth,@PathVariable String id,@RequestBody TimelineRegistrationService.PolicyUpdate request){service.configurePolicy(auth,id,request);return response(java.util.Map.of("status","OK"));}
    @GetMapping("/participant-flow") public ResponseEntity<?> flow(Authentication auth,@PathVariable String id){return response(service.read(auth,id));}
    @PostMapping("/participant-registration") public ResponseEntity<?> register(Authentication auth,@PathVariable String id,@RequestBody TimelineRegistrationService.Signup request){return response(service.register(auth,id,request));}
    @DeleteMapping("/participant-registration") public ResponseEntity<?> withdraw(Authentication auth,@PathVariable String id){return response(service.withdraw(auth,id));}
    @GetMapping("/participant-applications") public ResponseEntity<?> list(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(required=false)String status){return response(service.list(auth,id,page,status));}
    @PostMapping("/participant-applications/{application}/review") public ResponseEntity<?> review(Authentication auth,@PathVariable String id,@PathVariable java.util.UUID application,@RequestBody TimelineRegistrationService.Review request){service.review(auth,id,application,request);return response(java.util.Map.of("status","OK"));}
}
