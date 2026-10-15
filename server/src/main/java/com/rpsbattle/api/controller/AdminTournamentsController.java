package com.rpsbattle.api.controller;
import com.rpsbattle.admin.AdminCatalogService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/admin/tournaments")
public class AdminTournamentsController {
    private final AdminCatalogService service;public AdminTournamentsController(AdminCatalogService service){this.service=service;}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping public ResponseEntity<?> list(Authentication auth,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return response(service.tournaments(auth,filters,page,size));}
    @GetMapping("/{id}") public ResponseEntity<?> detail(Authentication auth,@PathVariable String id){return response(service.tournament(auth,id));}
    @PostMapping("/{id}/actions/{action}") public ResponseEntity<?> action(Authentication auth,@PathVariable String id,@PathVariable String action,@RequestBody Action body){return response(service.tournamentAction(auth,id,action,body.revision(),body.reason(),body.registrationId()));}
    public record Action(Long revision,String reason,String registrationId){}
}
