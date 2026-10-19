package com.rpsbattle.api.controller;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.remote.WorkerIdentityService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class RemoteWorkerController {
    private final WorkerIdentityService workers;
    private final AccountAuthorizationService accounts;
    public RemoteWorkerController(WorkerIdentityService workers, AccountAuthorizationService accounts){this.workers=workers;this.accounts=accounts;}
    public record Issue(Integer uses,Integer lifetimeMinutes) {}
    @PostMapping("/api/admin/workers/registration-tokens")
    public ResponseEntity<?> issue(Authentication auth,@RequestBody Issue body){var actor=accounts.requireAdmin(auth).id().value();return response(201,workers.issue(actor,body.uses()==null?1:body.uses(),body.lifetimeMinutes()==null?15:body.lifetimeMinutes()));}
    @GetMapping("/api/admin/workers/registration-tokens")
    public ResponseEntity<?> tokens(Authentication auth){accounts.requireAdmin(auth);return response(200,Map.of("items",workers.tokens()));}
    @PostMapping("/api/admin/workers/registration-tokens/{id}/revoke")
    public ResponseEntity<?> revoke(Authentication auth,@PathVariable UUID id){workers.revokeToken(id,accounts.requireAdmin(auth).id().value());return response(200,Map.of("revoked",true));}
    @PostMapping("/internal/workers/register")
    public ResponseEntity<?> register(@RequestBody WorkerIdentityService.Registration body){return response(201,workers.register(body));}
    @GetMapping("/internal/workers/{id}/identity")
    public ResponseEntity<?> identity(Authentication auth,@PathVariable UUID id){if(auth==null || !id.equals(auth.getPrincipal()))throw new AuthException("WORKER_FORBIDDEN",403);return response(200,workers.identity(id));}
    private static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
