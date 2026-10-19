package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.coding.FinalValidationService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/tournaments/{id}/final-validations")
public class FinalValidationController {
 private final FinalValidationService service;
 public FinalValidationController(FinalValidationService service){this.service=service;}
 @GetMapping public ResponseEntity<?> dashboard(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.dashboard(auth,id,page));}
 @PostMapping("/{validation}/{action}") public ResponseEntity<?> action(Authentication auth,@PathVariable String id,@PathVariable UUID validation,@PathVariable String action,@RequestBody FinalValidationService.Action request){service.action(auth,id,validation,action,request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","OK"));}
}
