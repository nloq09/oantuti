package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentPlayerController {
    private final TournamentDiscoveryService discovery;
    public TournamentPlayerController(TournamentDiscoveryService discovery){this.discovery=discovery;}
    @GetMapping("/discover") public ResponseEntity<?> discover(Authentication auth,@RequestParam(defaultValue="live") String category,@RequestParam(required=false)String q,@RequestParam(required=false)String format,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="12")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discovery.discover(auth,category,q,format,page,size));}
}
