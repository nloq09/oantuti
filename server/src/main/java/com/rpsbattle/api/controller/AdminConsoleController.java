package com.rpsbattle.api.controller;
import com.rpsbattle.admin.AdminReadService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin")
public class AdminConsoleController {
    private final AdminReadService reads;
    private final com.rpsbattle.admin.AdminDirectoryService directory;
    public AdminConsoleController(AdminReadService reads,com.rpsbattle.admin.AdminDirectoryService directory){this.reads=reads;this.directory=directory;}
    @GetMapping("/dashboard") public ResponseEntity<?> dashboard(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.dashboard(auth));}
    @GetMapping("/users/{id}") public ResponseEntity<?> user(Authentication auth,@PathVariable java.util.UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(directory.user(auth,id));}
}
