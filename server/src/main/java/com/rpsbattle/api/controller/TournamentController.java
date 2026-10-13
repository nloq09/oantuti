package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {
    private final TournamentService service;
    private final com.rpsbattle.bot.ownership.OwnedBotService bots;
    public TournamentController(TournamentService service,com.rpsbattle.bot.ownership.OwnedBotService bots){this.service=service;this.bots=bots;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody Create request){var t=service.create(request.name(),request.format());var response=new LinkedHashMap<>(service.view(t));response.put("ownerToken",t.ownerToken);return response;}
    @GetMapping public List<Map<String,Object>> list(){return service.list();}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable String id){return service.view(service.find(id));}
    @PostMapping("/{id}/participants") public Map<String,Object> add(org.springframework.security.core.Authentication auth,@PathVariable String id,@RequestBody Participant p){var bot=bots.execution(auth,p.botId());service.add(id,bot.id(),bot.token(),p.displayName(),p.seed());return detail(id);}
    @DeleteMapping("/{id}/participants/{participantId}") public Map<String,Object> remove(org.springframework.security.core.Authentication auth,@PathVariable String id,@PathVariable String participantId,@RequestHeader(value="X-Tournament-Token",required=false) String ownerToken) {
        if(ownerToken!=null) service.authorizeOwner(id,ownerToken);
        else {
            var tournament=service.find(id);
            synchronized(tournament) {
                var participant=tournament.participants.stream().filter(p->p.participantId().equals(participantId)).findFirst()
                    .orElseThrow(()->new com.rpsbattle.auth.AuthException("PARTICIPANT_NOT_FOUND",404));
                bots.authorizeWithdrawal(auth,participant.botId());
                service.remove(id,participantId);return detail(id);
            }
        }
        service.removeAuthorized(id,participantId,ownerToken,null);return detail(id);
    }
    @PostMapping("/{id}/start") public Map<String,Object> start(@PathVariable String id,@RequestHeader(value="X-Tournament-Token",required=false) String token){service.authorizeOwner(id,token);service.start(id);return detail(id);}
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(@PathVariable String id,@RequestHeader(value="X-Tournament-Token",required=false) String token){service.authorizeOwner(id,token);service.cancel(id);return detail(id);}
    @GetMapping("/{id}/ranking") public Object ranking(@PathVariable String id){return detail(id).get("ranking");}
    @GetMapping(value="/{id}/export",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> export(@PathVariable String id){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=tournament-"+id+".json").body(detail(id));}
    public record Create(String name,TournamentFormat format){}
    public record Participant(String botId,String botToken,String displayName,Long seed){}
}
