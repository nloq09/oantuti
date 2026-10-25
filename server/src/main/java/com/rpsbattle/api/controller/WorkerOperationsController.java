package com.rpsbattle.api.controller;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.worker.remote.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class WorkerOperationsController {
    @org.springframework.beans.factory.annotation.Autowired private WorkerOperationsService operations;
    @GetMapping("/api/admin/workers/operations") public ResponseEntity<?> operations(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String trust,@RequestParam(defaultValue="name") String sort){accounts.requireAdmin(auth);return ok(operations.workers(page,size,search,status,trust,sort));}
    @GetMapping("/api/admin/workers/operations/{id}") public ResponseEntity<?> operationsDetail(Authentication auth,@PathVariable UUID id){accounts.requireAdmin(auth);return ok(operations.detail(id));}
    @GetMapping("/api/admin/worker-assignments/tournaments") public ResponseEntity<?> tournamentOptions(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String search){accounts.requireAdmin(auth);return ok(operations.tournamentOptions(page,search));}
    @GetMapping("/api/admin/tournaments/{id}/worker-capacity") public ResponseEntity<?> capacity(Authentication auth,@PathVariable UUID id,@RequestParam(defaultValue="TRUSTED") String trust,@RequestParam(defaultValue="4") int requested){accounts.requireAdmin(auth);return ok(operations.capacity(id,trust,requested));}
    @org.springframework.beans.factory.annotation.Autowired private WorkerMetricsService metrics;
    @GetMapping("/api/admin/workers/metrics") public ResponseEntity<?> metrics(Authentication auth,@RequestParam(required=false) UUID tournamentId){accounts.requireAdmin(auth);return ok(metrics.read(tournamentId));}
    private final AccountAuthorizationService accounts;private final WorkerCapacityService capacity;private final RemoteRoutingService routing;private final WorkerTelemetryService telemetry;
    public WorkerOperationsController(AccountAuthorizationService accounts,WorkerCapacityService capacity,RemoteRoutingService routing,WorkerTelemetryService telemetry){this.accounts=accounts;this.capacity=capacity;this.routing=routing;this.telemetry=telemetry;}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @PostMapping("/api/admin/workers/{id}/capacity") public ResponseEntity<?> capacity(Authentication auth,@PathVariable UUID id,@RequestBody WorkerCapacityService.Limit body){capacity.limit(id,accounts.requireAdmin(auth).id().value(),body);return ok(telemetry.view(id));}
    @GetMapping("/api/admin/tournaments/{id}/worker-policy") public ResponseEntity<?> policy(Authentication auth,@PathVariable UUID id){accounts.requireAdmin(auth);return ok(routing.policy(id.toString()));}
    @PostMapping("/api/admin/tournaments/{id}/worker-policy") public ResponseEntity<?> policy(Authentication auth,@PathVariable UUID id,@RequestBody RemoteRoutingService.Configure body){return ok(routing.configure(id.toString(),accounts.requireAdmin(auth).id().value(),body));}
}
