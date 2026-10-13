package com.rpsbattle.api.controller;

import com.rpsbattle.account.AdminService;
import com.rpsbattle.account.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) {this.service=service;}
    @GetMapping("/users") public ResponseEntity<Map<String,Object>> users(Authentication authentication,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result=service.list(authentication,page,size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("users",result.users().stream().map(AccountController.Profile::from).toList(),
                "total",result.total(),"page",result.page(),"size",result.size()));
    }
    @PostMapping("/users/{id}/disable") public ResponseEntity<AccountController.Profile> disable(Authentication authentication,@PathVariable UUID id) {
        return status(authentication,id,AccountStatus.DISABLED);
    }
    @PostMapping("/users/{id}/enable") public ResponseEntity<AccountController.Profile> enable(Authentication authentication,@PathVariable UUID id) {
        return status(authentication,id,AccountStatus.ACTIVE);
    }
    private ResponseEntity<AccountController.Profile> status(Authentication authentication,UUID id,AccountStatus status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AccountController.Profile.from(service.setStatus(authentication,new UserId(id),status)));
    }
    @GetMapping("/system") public ResponseEntity<Map<String,Object>> system(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.system(authentication));
    }
}
