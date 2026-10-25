package com.rpsbattle.api.controller;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.remote.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class TournamentWorkerController {
    private final TournamentWorkerService workers;
    public TournamentWorkerController(TournamentWorkerService workers){this.workers=workers;}
    public record Issue(Boolean trusted){}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/api/tournaments/{id}/workers") public ResponseEntity<?> list(Authentication auth,@PathVariable String id){return ok(workers.list(auth,id));}
    @PostMapping("/api/tournaments/{id}/workers/registration-tokens") public ResponseEntity<?> issue(Authentication auth,@PathVariable String id,@RequestBody Issue body){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(workers.issue(auth,id,body!=null&&Boolean.TRUE.equals(body.trusted())));}
    @PostMapping("/api/tournaments/{id}/workers/registration-tokens/{token}/revoke") public ResponseEntity<?> revoke(Authentication auth,@PathVariable String id,@PathVariable UUID token){workers.revokeToken(auth,id,token);return ok(Map.of("revoked",true));}
    @PostMapping("/api/tournaments/{id}/workers/{worker}/control") public ResponseEntity<?> control(Authentication auth,@PathVariable String id,@PathVariable UUID worker,@RequestBody WorkerControlService.Command body){return ok(workers.control(auth,id,worker,body));}
    @GetMapping("/api/tournaments/{id}/workers/download") public ResponseEntity<org.springframework.core.io.Resource> download(Authentication auth,@PathVariable String id){workers.downloadAllowed(auth,id);var jar=new org.springframework.core.io.ClassPathResource("worker-distribution/rps-worker.jar");if(!jar.exists())throw new AuthException("WORKER_DOWNLOAD_UNAVAILABLE",503);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"rps-worker.jar\"").contentType(MediaType.APPLICATION_OCTET_STREAM).body(jar);}
}
