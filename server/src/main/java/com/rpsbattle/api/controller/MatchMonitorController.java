package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.MatchMonitorService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/tournaments/{id}")
public class MatchMonitorController {
 private final MatchMonitorService service;private final com.rpsbattle.tournament.RemoteExecutionReadService remote;
 public MatchMonitorController(MatchMonitorService service,com.rpsbattle.tournament.RemoteExecutionReadService remote){this.service=service;this.remote=remote;}
 @GetMapping("/match-monitor/{match}/attempts") public ResponseEntity<?> attempts(Authentication auth,@PathVariable String id,@PathVariable String match){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(remote.history(auth,id,match));}
 @GetMapping({"/match-monitor","/my-matches"}) public ResponseEntity<?> page(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="ALL") String status,jakarta.servlet.http.HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(service.page(auth,id,page,size,status,request.getRequestURI().endsWith("/my-matches")));}
}
