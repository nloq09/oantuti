package com.rpsbattle.api.controller;
import com.rpsbattle.admin.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/admin")
public class AdminReviewsController {
    private final AdminDisputeService disputes;private final AdminReplayService replays;
    public AdminReviewsController(AdminDisputeService disputes,AdminReplayService replays){this.disputes=disputes;this.replays=replays;}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/disputes") public ResponseEntity<?> disputes(Authentication auth,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return response(disputes.list(auth,filters,page,size));}
    @GetMapping("/disputes/{id}") public ResponseEntity<?> dispute(Authentication auth,@PathVariable UUID id){return response(disputes.detail(auth,id));}
    @PostMapping("/disputes/{id}/{decision}") public ResponseEntity<?> decide(Authentication auth,@PathVariable UUID id,@PathVariable String decision,@RequestBody Decision body){return response(disputes.decide(auth,id,decision,body.revision(),body.reason(),body.winnerParticipantId(),body.participantId()));}
    @GetMapping("/replays") public ResponseEntity<?> replays(Authentication auth,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return response(replays.list(auth,filters,page,size));}
    @GetMapping("/replays/{id}") public ResponseEntity<?> replay(Authentication auth,@PathVariable String id){return response(replays.detail(auth,id));}
    @PostMapping("/replays/{id}/validate") public ResponseEntity<?> validate(Authentication auth,@PathVariable String id,@RequestBody AdminController.Reason body){return response(replays.validate(auth,id,body.reason()));}
    public record Decision(Long revision,String reason,String winnerParticipantId,String participantId){}
}
