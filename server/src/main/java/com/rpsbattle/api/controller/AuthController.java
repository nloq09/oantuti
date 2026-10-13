package com.rpsbattle.api.controller;

import com.rpsbattle.account.domain.User;
import com.rpsbattle.auth.*;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthService auth;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    @Value("${server.servlet.session.cookie.secure:false}") private boolean secureCookie;
    public AuthController(RegistrationService registration,AuthService auth,SessionAuthenticationStrategy sessions,SecurityContextRepository contexts) {
        this.registration=registration;this.auth=auth;this.sessions=sessions;this.contexts=contexts;
    }
    @GetMapping("/csrf")
    public ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token",token.getToken(),"headerName",token.getHeaderName()));
    }
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody Register request) {
        var user=registration.register(request.username(),request.email(),request.password(),request.displayName());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(UserResponse.from(user));
    }
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody Login body,HttpServletRequest request,HttpServletResponse response) {
        var authentication=auth.authenticate(body.username(),body.password());
        var user=auth.markLogin(authentication);
        sessions.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UserResponse.from(user));
    }
    @PostMapping("/logout")
    public ResponseEntity<Map<String,Boolean>> logout(Authentication authentication,HttpServletRequest request,HttpServletResponse response) {
        var handler=new SecurityContextLogoutHandler();handler.setSecurityContextRepository(contexts);
        handler.logout(request,response,authentication);
        var cookie=ResponseCookie.from("JSESSIONID","").httpOnly(true).secure(secureCookie || request.isSecure()).sameSite("Lax").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authenticated",false));
    }
    @GetMapping("/me")
    public ResponseEntity<Map<String,Object>> me(Authentication authentication,HttpServletRequest request,HttpServletResponse response) {
        var current=auth.current(authentication);
        if (current.isEmpty() && authentication!=null) {
            var handler=new SecurityContextLogoutHandler();handler.setSecurityContextRepository(contexts);handler.logout(request,response,authentication);
        }
        Map<String,Object> body=current.<Map<String,Object>>map(user -> Map.of("authenticated",true,"user",UserResponse.from(user)))
                .orElseGet(()->Map.of("authenticated",false));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    public record Register(String username,String email,String password,String displayName) {
        @Override public String toString() { return "Register[REDACTED]"; }
    }
    public record Login(String username,String password) {
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    public record UserResponse(String id,String username,String displayName,String role) {
        public static UserResponse from(User user) { return new UserResponse(user.id().toString(),user.username(),user.displayName(),user.role().name()); }
    }
}
