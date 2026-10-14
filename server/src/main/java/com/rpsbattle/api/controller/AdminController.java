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
    private final com.rpsbattle.admin.AdminDirectoryService directory;
    public AdminController(AdminService service,com.rpsbattle.admin.AdminDirectoryService directory) {this.service=service;this.directory=directory;}
    @GetMapping("/users") public ResponseEntity<Map<String,Object>> users(Authentication authentication,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam Map<String,String> filters) {
        var result=new LinkedHashMap<>(directory.list(authentication,"users",filters,page,size));
        result.put("users",result.get("items"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping("/users/{id}/disable") public ResponseEntity<AccountController.Profile> disable(Authentication authentication,@PathVariable UUID id,@RequestBody Reason body) {
        return status(authentication,id,AccountStatus.DISABLED,body.reason());
    }
    @PostMapping("/users/{id}/enable") public ResponseEntity<AccountController.Profile> enable(Authentication authentication,@PathVariable UUID id,@RequestBody Reason body) {
        return status(authentication,id,AccountStatus.ACTIVE,body.reason());
    }
    private ResponseEntity<AccountController.Profile> status(Authentication authentication,UUID id,AccountStatus status,String reason) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AccountController.Profile.from(directory.userStatus(authentication,id,status,reason)));
    }
    public record Reason(String reason){}
    @GetMapping("/system") public ResponseEntity<Map<String,Object>> system(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.system(authentication));
    }
}
