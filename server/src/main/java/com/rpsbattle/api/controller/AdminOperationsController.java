package com.rpsbattle.api.controller;
import com.rpsbattle.admin.AdminAuditService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/admin/audit")
public class AdminOperationsController {
    private final AdminAuditService audit;public AdminOperationsController(AdminAuditService audit){this.audit=audit;}
    @GetMapping public ResponseEntity<?> audit(Authentication auth,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.list(auth,filters,page,size));}
}
