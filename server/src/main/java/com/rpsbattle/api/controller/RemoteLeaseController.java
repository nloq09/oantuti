package com.rpsbattle.api.controller;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.remote.RemoteLeaseService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class RemoteLeaseController {
    private final RemoteLeaseService leases;
    public RemoteLeaseController(RemoteLeaseService leases){this.leases=leases;}
    private static void own(Authentication auth,UUID id){if(auth==null||!id.equals(auth.getPrincipal()))throw new AuthException("WORKER_FORBIDDEN",403);}
    @PostMapping("/internal/workers/{id}/jobs/renew") public ResponseEntity<?> renew(Authentication auth,@PathVariable UUID id,@RequestBody RemoteLeaseService.Renew body){own(auth,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leases.renew(id,body));}
    @PostMapping("/internal/workers/{id}/jobs/recover") public ResponseEntity<?> recover(Authentication auth,@PathVariable UUID id,@RequestBody RemoteLeaseService.Recovery body){own(auth,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leases.recover(id,body));}
}
