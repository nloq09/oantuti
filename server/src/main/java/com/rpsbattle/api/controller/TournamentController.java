package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentController {
    private final TournamentService service;
    private final OwnedTournamentService owned;
    public TournamentController(TournamentService service,OwnedTournamentService owned) {this.service=service;this.owned=owned;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth,@RequestBody Create request) {return service.view(owned.create(auth,request.name(),request.format(),request.visibility(),Boolean.TRUE.equals(request.checkInRequired()),request.maxParticipants(),request.maxConcurrentMatches(),request.tieBreakOrder()));}
    @GetMapping public List<Map<String,Object>> list() {owned.flush();return service.list();}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable String id) {owned.flush();return service.view(service.find(id));}
    @PostMapping("/{id}/participants") public Map<String,Object> add(Authentication auth,@PathVariable String id,@RequestBody Participant p) {owned.add(auth,id,p.botId(),p.displayName(),p.seed());return detail(id);}
    @DeleteMapping("/{id}/participants/{participantId}") public Map<String,Object> remove(Authentication auth,@PathVariable String id,@PathVariable String participantId) {owned.remove(auth,id,participantId);return detail(id);}
    @PostMapping("/{id}/start") public Map<String,Object> start(Authentication auth,@PathVariable String id) {owned.start(auth,id);return detail(id);}
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(Authentication auth,@PathVariable String id) {owned.cancel(auth,id);return detail(id);}
    @PostMapping("/{id}/open-registration") public Map<String,Object> openRegistration(Authentication auth,@PathVariable String id) {owned.openRegistration(auth,id);return detail(id);}
    @PostMapping("/{id}/close-registration") public Map<String,Object> closeRegistration(Authentication auth,@PathVariable String id) {owned.closeRegistration(auth,id);return detail(id);}
    @PostMapping("/{id}/registrations") public Map<String,Object> register(Authentication auth,@PathVariable String id,@RequestBody Participant p) {owned.register(auth,id,p.botId());return detail(id);}
    @PostMapping("/{id}/registrations/{registrationId}/approve") public Map<String,Object> approve(Authentication auth,@PathVariable String id,@PathVariable String registrationId) {owned.review(auth,id,registrationId,true);return detail(id);}
    @PostMapping("/{id}/registrations/{registrationId}/reject") public Map<String,Object> reject(Authentication auth,@PathVariable String id,@PathVariable String registrationId) {owned.review(auth,id,registrationId,false);return detail(id);}
    @DeleteMapping("/{id}/registrations/{registrationId}") public Map<String,Object> withdraw(Authentication auth,@PathVariable String id,@PathVariable String registrationId) {owned.withdraw(auth,id,registrationId);return detail(id);}
    @PostMapping("/{id}/check-in") public Map<String,Object> checkIn(Authentication auth,@PathVariable String id,@RequestBody(required=false) CheckIn request) {owned.checkIn(auth,id,request==null?null:request.registrationId());return detail(id);}
    @PostMapping("/{id}/close-check-in") public Map<String,Object> closeCheckIn(Authentication auth,@PathVariable String id) {owned.closeCheckIn(auth,id);return detail(id);}
    @PostMapping("/{id}/rules") public Map<String,Object> rules(Authentication auth,@PathVariable String id,@RequestBody Rules request) {owned.configureRules(auth,id,request.maxConcurrentMatches(),request.tieBreakOrder());return detail(id);}
    public record Rules(int maxConcurrentMatches,List<com.rpsbattle.tournament.ranking.TieBreak> tieBreakOrder) {}
    public record CheckIn(String registrationId) {}
    @PostMapping("/{id}/seeding") public Map<String,Object> seeding(Authentication auth,@PathVariable String id,@RequestBody Seeds request){owned.configureSeeding(auth,id,request.strategy(),request.seed(),request.manualSeeds());return detail(id);}
    public record Seeds(SeedingStrategy strategy,Long seed,Map<String,Integer> manualSeeds) {}
    @GetMapping("/{id}/ranking") public Object ranking(@PathVariable String id) {return detail(id).get("ranking");}
    @GetMapping(value="/{id}/export",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> export(@PathVariable String id) {return ResponseEntity.ok().header("Content-Disposition","attachment; filename=tournament-"+id+".json").body(detail(id));}
    public record Create(String name,TournamentFormat format,String visibility,Boolean checkInRequired,Integer maxParticipants,Integer maxConcurrentMatches,List<com.rpsbattle.tournament.ranking.TieBreak> tieBreakOrder) {}
    public record Participant(String botId,String displayName,Long seed) {}
}
