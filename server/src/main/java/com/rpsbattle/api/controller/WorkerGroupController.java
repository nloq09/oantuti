package com.rpsbattle.api.controller;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.worker.remote.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class WorkerGroupController {
    @org.springframework.beans.factory.annotation.Autowired private WorkerMetricsService metrics;
    @GetMapping("/api/admin/workers/metrics") public ResponseEntity<?> metrics(Authentication auth,@RequestParam(required=false) UUID tournamentId){accounts.requireAdmin(auth);return ok(metrics.read(tournamentId));}
    private final AccountAuthorizationService accounts;private final WorkerGroupService groups;private final RemoteRoutingService routing;private final WorkerTelemetryService telemetry;
    public WorkerGroupController(AccountAuthorizationService accounts,WorkerGroupService groups,RemoteRoutingService routing,WorkerTelemetryService telemetry){this.accounts=accounts;this.groups=groups;this.routing=routing;this.telemetry=telemetry;}
    public record Create(String name,String reason){}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/api/admin/worker-groups") public ResponseEntity<?> list(Authentication auth){accounts.requireAdmin(auth);return ok(Map.of("items",groups.list()));}
    @PostMapping("/api/admin/worker-groups") public ResponseEntity<?> create(Authentication auth,@RequestBody Create body){return ok(groups.create(body.name(),accounts.requireAdmin(auth).id().value(),body.reason()));}
    @PostMapping("/api/admin/worker-groups/{id}") public ResponseEntity<?> change(Authentication auth,@PathVariable UUID id,@RequestBody WorkerGroupService.Change body){return ok(groups.change(id,accounts.requireAdmin(auth).id().value(),body));}
    @PostMapping("/api/admin/workers/{id}/capacity") public ResponseEntity<?> capacity(Authentication auth,@PathVariable UUID id,@RequestBody WorkerGroupService.Limit body){groups.limit(id,accounts.requireAdmin(auth).id().value(),body);return ok(telemetry.view(id));}
    @GetMapping("/api/admin/tournaments/{id}/worker-policy") public ResponseEntity<?> policy(Authentication auth,@PathVariable UUID id){accounts.requireAdmin(auth);return ok(routing.policy(id.toString()));}
    @PostMapping("/api/admin/tournaments/{id}/worker-policy") public ResponseEntity<?> policy(Authentication auth,@PathVariable UUID id,@RequestBody RemoteRoutingService.Configure body){return ok(routing.configure(id.toString(),accounts.requireAdmin(auth).id().value(),body));}
}
