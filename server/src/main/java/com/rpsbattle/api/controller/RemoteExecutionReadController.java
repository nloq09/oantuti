package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.RemoteExecutionReadService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/tournaments/{id}/remote-executions")
public class RemoteExecutionReadController {
 private final RemoteExecutionReadService service;
 public RemoteExecutionReadController(RemoteExecutionReadService service){this.service=service;}
 @GetMapping public ResponseEntity<?> page(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(required=false) String status){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(service.read(auth,id,page,size,status));}
}
