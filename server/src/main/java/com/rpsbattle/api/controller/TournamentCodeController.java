package com.rpsbattle.api.controller;

import com.rpsbattle.tournament.coding.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/tournaments/{id}/workspace")
public class TournamentCodeController {
 private final CodeWorkspaceService service;
 public TournamentCodeController(CodeWorkspaceService service){this.service=service;}
 private ResponseEntity<?> response(Object body,int status){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Vary","Cookie").body(body);}
 public record Create(String language){}
 public record Change(String language,Long revision,String sourceCode){}
 @PostMapping public ResponseEntity<?> create(Authentication auth,@PathVariable String id,@RequestBody Create request){return response(service.create(auth,id,request.language()),200);}
 @GetMapping("/me") public ResponseEntity<?> read(Authentication auth,@PathVariable String id,@RequestParam(required=false)String language){return response(service.read(auth,id,language),200);}
 @GetMapping("/versions/{version}/source") public ResponseEntity<?> source(Authentication auth,@PathVariable String id,@PathVariable java.util.UUID version){return response(service.source(auth,id,version),200);}
 @PutMapping("/me") public ResponseEntity<?> save(Authentication auth,@PathVariable String id,@RequestBody Change request){return response(service.save(auth,id,request.language(),request.revision(),request.sourceCode()),200);}
 @PostMapping("/me/build") public ResponseEntity<?> build(Authentication auth,@PathVariable String id,@RequestBody Change request){return response(service.job(auth,id,request.language(),request.revision(),request.sourceCode(),"BUILD"),202);}
 @PostMapping("/me/test") public ResponseEntity<?> test(Authentication auth,@PathVariable String id,@RequestBody Change request){return response(service.job(auth,id,request.language(),request.revision(),null,"TEST"),202);}
 @PostMapping("/me/test-match") public ResponseEntity<?> sample(Authentication auth,@PathVariable String id,@RequestBody Change request){return response(service.job(auth,id,request.language(),request.revision(),null,"SAMPLE"),202);}
 @PostMapping("/me/validate") public ResponseEntity<?> validate(Authentication auth,@PathVariable String id,@RequestBody Change request){return response(service.job(auth,id,request.language(),request.revision(),null,"VALIDATE"),202);}
 @GetMapping("/public-tests") public ResponseEntity<?> cases(Authentication auth,@PathVariable String id){return response(service.publicCases(auth,id),200);}
}
