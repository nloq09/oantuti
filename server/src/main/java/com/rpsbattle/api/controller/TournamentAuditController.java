package com.rpsbattle.api.controller;
import com.rpsbattle.admin.TournamentAuditReadService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tournaments/{id}/audit")
public class TournamentAuditController {
    private final TournamentAuditReadService audit;
    public TournamentAuditController(TournamentAuditReadService audit){this.audit=audit;}
    @GetMapping public ResponseEntity<?> list(Authentication auth,@PathVariable String id,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.list(auth,id,filters,page,size));}
}
