package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.coding.*;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/tournaments/{id}")
public class TournamentSubmissionController {
 @org.springframework.beans.factory.annotation.Autowired SubmissionAttemptService attempts;
 private final TournamentSubmissionService service;private final PreuploadedSubmissionService imports;private final CodingQueueStore queue;private final TournamentService tournaments;private final TournamentAuthorizationService authz;
 public TournamentSubmissionController(TournamentSubmissionService s,PreuploadedSubmissionService p,CodingQueueStore q,TournamentService t,TournamentAuthorizationService a){service=s;imports=p;queue=q;tournaments=t;authz=a;}
 private ResponseEntity<?> response(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(body==null?Map.of("status","NO_SUBMISSION"):body);}
 public record Submit(UUID versionId,Long revision,boolean rulesAccepted){}
 public record Attempt(UUID requestId,String language,String sourceCode,String botId,boolean rulesAccepted){}
 public record Import(String botId){}
 public record Action(String action,String reason,Long revision){}
 @GetMapping("/submission/me") public ResponseEntity<?> mine(Authentication auth,@PathVariable String id){return response(service.mine(auth,id));}
 @GetMapping("/submission/me/versions") public ResponseEntity<?> versions(Authentication auth,@PathVariable String id){return response(service.versions(auth,id));}
 @GetMapping("/submission/me/attempts/latest") public ResponseEntity<?> attempt(Authentication auth,@PathVariable String id){return response(attempts.latest(auth,id));}
 @PostMapping("/submission/me/attempts") public ResponseEntity<?> attempt(Authentication auth,@PathVariable String id,@RequestBody Attempt request){return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(attempts.start(auth,id,request.requestId(),request.language(),request.sourceCode(),request.botId(),request.rulesAccepted()));}
 @PostMapping("/workspace/me/submit") public ResponseEntity<?> submit(Authentication auth,@PathVariable String id,@RequestBody Submit request){if(request.revision()==null)throw new com.rpsbattle.auth.AuthException("STALE_TOURNAMENT_REVISION",409);service.submit(auth,id,request.versionId(),request.revision(),request.rulesAccepted());return mine(auth,id);}
 @PostMapping("/submission/preuploaded") public ResponseEntity<?> importBot(Authentication auth,@PathVariable String id,@RequestBody Import request){return response(imports.importBot(auth,id,request.botId()));}
 @GetMapping("/submissions") public ResponseEntity<?> dashboard(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@RequestParam(defaultValue="versions")String view,@RequestParam(defaultValue="ALL")String filter,@RequestParam(defaultValue="")String search){return response(view.equals("participants")?service.participants(auth,id,page,size,filter,search):service.dashboard(auth,id,page));}
 @PostMapping("/submissions/{submissionId}/action") public ResponseEntity<?> action(Authentication auth,@PathVariable String id,@PathVariable UUID submissionId,@RequestBody Action request){if(request.revision()==null)throw new com.rpsbattle.auth.AuthException("STALE_TOURNAMENT_REVISION",409);service.action(auth,id,submissionId,request.action(),request.reason(),request.revision());return response(Map.of("status","OK"));}
 @GetMapping("/coding-jobs") public ResponseEntity<?> jobs(Authentication auth,@PathVariable String id){authz.requireManager(auth,tournaments.find(id));return response(queue.jobs(UUID.fromString(id)));}
}
