package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/tournaments")
public class TournamentPlayerController {
    private final TournamentDiscoveryService discovery;private final ParticipantTournamentStateService participants;private final OwnedTournamentService owned;private final TournamentReadyService readiness;
    public TournamentPlayerController(TournamentDiscoveryService discovery,ParticipantTournamentStateService participants,OwnedTournamentService owned,TournamentReadyService readiness){this.discovery=discovery;this.participants=participants;this.owned=owned;this.readiness=readiness;}
    @org.springframework.beans.factory.annotation.Autowired private TournamentMatchContextService context;
    @org.springframework.beans.factory.annotation.Autowired private ParticipantWithdrawalService withdrawals;
    @PostMapping("/{id}/withdraw") public ResponseEntity<?> withdraw(Authentication auth,@PathVariable String id,@RequestBody Withdrawal body){withdrawals.withdraw(auth,id,body.revision(),body.participantId(),body.confirmed());return me(auth,id);}
    public record Withdrawal(Long revision,String participantId,boolean confirmed){}
    @PostMapping("/{id}/stage-plan") public ResponseEntity<?> stagePlan(Authentication auth,@PathVariable String id,@RequestBody StagePlan body){owned.stagePlan(auth,id,body.revision(),body.stages());return summary(id);}
    public record StagePlan(Long revision,java.util.List<com.rpsbattle.tournament.domain.GroupRoundPlan> stages){}
    @org.springframework.beans.factory.annotation.Autowired private TournamentMatchPageService pages;
    @org.springframework.beans.factory.annotation.Autowired private TournamentService tournaments;
    @org.springframework.beans.factory.annotation.Autowired private PlayerTournamentPageService playerPages;
    @GetMapping("/{id}/player-pages/{section}") public ResponseEntity<?> playerPage(@PathVariable String id,@PathVariable String section,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playerPages.page(id,section,page,size));}
    @GetMapping("/{id}/player-extras/{section}") public ResponseEntity<?> playerExtra(@PathVariable String id,@PathVariable String section){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playerPages.extra(id,section));}
    public ResponseEntity<?> summary(String id){return summary(id,false);}
    @GetMapping("/{id}/summary") public ResponseEntity<?> summary(@PathVariable String id,@RequestParam(defaultValue="false")boolean light){var t=tournaments.find(id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(light?tournaments.playerSummary(t):tournaments.summary(t));}
    @GetMapping("/{id}/bracket") public ResponseEntity<?> bracket(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pages.bracket(id));}
    @GetMapping("/{id}/fixtures/{fixtureId}/matches") public ResponseEntity<?> fixture(@PathVariable String id,@PathVariable String fixtureId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pages.fixture(id,fixtureId));}
    @GetMapping("/{id}/matches") public ResponseEntity<?> matches(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)Integer round,@RequestParam(required=false)Integer stage,@RequestParam(required=false)String status,@RequestParam(defaultValue="false")boolean mine,@RequestParam(defaultValue="round")String order,@RequestParam(defaultValue="false")boolean replay,@RequestParam(defaultValue="false")boolean groupBySeries){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pages.page(auth,id,page,size,round,stage,status,mine,order,replay,groupBySeries));}
    @GetMapping("/{id}/match-context") public ResponseEntity<?> context(@PathVariable String id,@RequestParam String onlineMatchId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(context.context(id,onlineMatchId));}
    @PostMapping("/{id}/ready") public ResponseEntity<?> ready(Authentication auth,@PathVariable String id,@RequestBody Ready body){readiness.ready(auth,id,body.matchId(),body.windowId());return me(auth,id);}
    @PostMapping("/{id}/entry") public ResponseEntity<?> entry(Authentication auth,@PathVariable String id,@RequestBody Ready body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readiness.entry(auth,id,body.matchId()));}
    public record Ready(String matchId,String windowId){}
    @PostMapping("/{id}/timing") public ResponseEntity<?> timing(Authentication auth,@PathVariable String id,@RequestBody Timing body){owned.configureTiming(auth,id,body.revision(),new com.rpsbattle.tournament.domain.TournamentTiming(body.registrationDeadline(),body.checkInDeadline(),body.startTime()));return ResponseEntity.noContent().build();}
    public record Timing(Long revision,java.time.Instant registrationDeadline,java.time.Instant checkInDeadline,java.time.Instant startTime){}
    @GetMapping("/{id}/me") public ResponseEntity<?> me(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(participants.me(auth,id));}
    @GetMapping("/discover") public ResponseEntity<?> discover(Authentication auth,@RequestParam(defaultValue="live") String category,@RequestParam(required=false)String q,@RequestParam(required=false)String format,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="12")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discovery.discover(auth,category,q,format,page,size));}
}
