package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.OrganizerMetricsService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/tournaments/{id}/organizer-metrics")
public class OrganizerMetricsController {
 private final OrganizerMetricsService service;
 public OrganizerMetricsController(OrganizerMetricsService service){this.service=service;}
 @GetMapping public ResponseEntity<OrganizerMetricsService.View> read(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(service.read(auth,id));}
}
