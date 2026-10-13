package com.rpsbattle.api.controller;

import com.rpsbattle.account.AccountService;
import com.rpsbattle.account.domain.User;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService service;
    private final SecurityContextRepository contexts;
    public AccountController(AccountService service,SecurityContextRepository contexts) {this.service=service;this.contexts=contexts;}
    @GetMapping({"","/profile"}) public ResponseEntity<Profile> profile(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Profile.from(service.profile(authentication)));
    }
    @PatchMapping("/profile") public ResponseEntity<Profile> update(Authentication authentication,@RequestBody Update body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Profile.from(service.updateProfile(authentication,body.displayName())));
    }
    @PostMapping("/change-password") public ResponseEntity<Map<String,Boolean>> changePassword(Authentication authentication,
            @RequestBody Password body,HttpServletRequest request,HttpServletResponse response) {
        service.changePassword(authentication,body.currentPassword(),body.newPassword());
        var logout=new SecurityContextLogoutHandler();logout.setSecurityContextRepository(contexts);logout.logout(request,response,authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("changed",true,"authenticated",false));
    }
    public record Update(String displayName) {}
    public record Password(String currentPassword,String newPassword) {
        @Override public String toString() {return "Password[REDACTED]";}
    }
    public record Profile(String id,String username,String email,String displayName,String role,String status,Instant createdAt,Instant updatedAt,Instant lastLoginAt) {
        public static Profile from(User user) {return new Profile(user.id().toString(),user.username(),user.email(),user.displayName(),user.role().name(),user.status().name(),user.createdAt(),user.updatedAt(),user.lastLoginAt());}
    }
}
