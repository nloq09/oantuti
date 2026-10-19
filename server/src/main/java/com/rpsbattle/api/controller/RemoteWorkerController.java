package com.rpsbattle.api.controller;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.remote.WorkerIdentityService;
import com.rpsbattle.worker.remote.WorkerTelemetryService;
import com.rpsbattle.worker.remote.RemoteMatchQueue;
import com.rpsbattle.worker.remote.RemoteArtifactService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class RemoteWorkerController {
    private final WorkerIdentityService workers;
    private final AccountAuthorizationService accounts;
    private final WorkerTelemetryService telemetry;
    private final RemoteMatchQueue queue;
    private final RemoteArtifactService artifacts;
    public RemoteWorkerController(WorkerIdentityService workers, AccountAuthorizationService accounts,WorkerTelemetryService telemetry,RemoteMatchQueue queue,RemoteArtifactService artifacts){this.workers=workers;this.accounts=accounts;this.telemetry=telemetry;this.queue=queue;this.artifacts=artifacts;}
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
    @PostMapping("/internal/workers/{id}/capabilities")
    public ResponseEntity<?> capabilities(Authentication auth,@PathVariable UUID id,@RequestBody WorkerTelemetryService.Capabilities body){own(auth,id);return response(200,telemetry.capabilities(id,body));}
    @PostMapping("/internal/workers/{id}/heartbeat")
    public ResponseEntity<?> heartbeat(Authentication auth,@PathVariable UUID id,@RequestBody WorkerTelemetryService.Heartbeat body,jakarta.servlet.http.HttpServletRequest request){own(auth,id);return response(200,telemetry.heartbeat(id,body,request.getRemoteAddr()));}
    @GetMapping("/api/admin/workers")
    public ResponseEntity<?> list(Authentication auth,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){accounts.requireAdmin(auth);return response(200,telemetry.list(page,size));}
    @GetMapping("/api/admin/workers/{id}")
    public ResponseEntity<?> detail(Authentication auth,@PathVariable UUID id){accounts.requireAdmin(auth);return response(200,telemetry.view(id));}
    @PostMapping("/api/admin/workers/{id}/review")
    public ResponseEntity<?> review(Authentication auth,@PathVariable UUID id,@RequestBody WorkerTelemetryService.Review body){return response(200,telemetry.review(id,accounts.requireAdmin(auth).id().value(),body));}
    @GetMapping("/api/admin/workers/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(Authentication auth){accounts.requireAdmin(auth);var jar=new org.springframework.core.io.ClassPathResource("worker-distribution/rps-worker.jar");if(!jar.exists())throw new AuthException("WORKER_DOWNLOAD_UNAVAILABLE",503);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"rps-worker.jar\"").contentType(MediaType.APPLICATION_OCTET_STREAM).body(jar);}
    private static void own(Authentication auth,UUID id){if(auth==null||!id.equals(auth.getPrincipal()))throw new AuthException("WORKER_FORBIDDEN",403);}
    @PostMapping("/internal/workers/{id}/jobs/claim") public ResponseEntity<?> claim(Authentication auth,@PathVariable UUID id,@RequestBody RemoteMatchQueue.Claim body){own(auth,id);return response(200,queue.claim(id,body));}
    @GetMapping("/internal/workers/{id}/jobs/assigned") public ResponseEntity<?> assigned(Authentication auth,@PathVariable UUID id){own(auth,id);return response(200,queue.assigned(id));}
    @GetMapping("/internal/workers/{id}/artifacts/{artifact}") public ResponseEntity<byte[]> artifact(Authentication auth,@PathVariable UUID id,@PathVariable UUID artifact,@RequestParam UUID executionId){own(auth,id);var data=artifacts.download(id,executionId,artifact);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(data.bytes().length).header("X-Artifact-SHA256",data.hash()).header("X-Artifact-Language",data.language()).header("X-Artifact-Format",data.format()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"artifact.bin\"").body(data.bytes());}
    @PostMapping("/internal/workers/{id}/jobs/{job}/decline") public ResponseEntity<?> decline(Authentication auth,@PathVariable UUID id,@PathVariable UUID job,@RequestBody RemoteMatchQueue.Decline body){own(auth,id);return response(200,queue.decline(id,job,body));}
    @GetMapping("/admin/workers") public ResponseEntity<Void> workerPage(){return ResponseEntity.status(302).location(java.net.URI.create("/admin/workers.html")).build();}
    @GetMapping("/admin/workers/setup") public ResponseEntity<Void> setupPage(){return ResponseEntity.status(302).location(java.net.URI.create("/admin/worker-setup.html")).build();}
    private static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
