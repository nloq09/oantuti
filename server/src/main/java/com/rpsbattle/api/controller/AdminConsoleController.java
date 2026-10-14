package com.rpsbattle.api.controller;
import com.rpsbattle.admin.AdminReadService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin")
public class AdminConsoleController {
    private final AdminReadService reads;
    public AdminConsoleController(AdminReadService reads){this.reads=reads;}
    @GetMapping("/dashboard") public ResponseEntity<?> dashboard(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.dashboard(auth));}
}
