package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/tournaments/{id}")
public class TournamentAdministrationController {
    private final TournamentAdministrationService admin;private final TournamentService tournaments;private final TournamentAuthorizationService authorization;
    public TournamentAdministrationController(TournamentAdministrationService admin,TournamentService tournaments,TournamentAuthorizationService authorization){this.admin=admin;this.tournaments=tournaments;this.authorization=authorization;}
    @PostMapping("/control/{action}") public Object control(Authentication auth,@PathVariable String id,@PathVariable String action,@RequestBody Control body){admin.control(auth,id,body.revision(),action,body.reason());return tournaments.view(tournaments.find(id));}
    @PostMapping("/round-policy") public Object rounds(Authentication auth,@PathVariable String id,@RequestBody RoundPolicy body){admin.roundPolicy(auth,id,body.revision(),body.autoAdvanceRounds());return tournaments.view(tournaments.find(id));}
    @PostMapping("/participants/{participantId}/status") public Object participant(Authentication auth,@PathVariable String id,@PathVariable String participantId,@RequestBody Participant body){admin.participant(auth,id,body.revision(),participantId,body.status(),body.reason());return tournaments.view(tournaments.find(id));}
    @PostMapping("/matches/{matchId}/review") public Object review(Authentication auth,@PathVariable String id,@PathVariable String matchId,@RequestBody Review body){admin.review(auth,id,body.revision(),matchId,body.reason(),body.keepResult());return tournaments.view(tournaments.find(id));}
    @PostMapping("/matches/{matchId}/retry") public Object retry(Authentication auth,@PathVariable String id,@PathVariable String matchId,@RequestBody Control body){admin.retry(auth,id,body.revision(),matchId,body.reason());return tournaments.view(tournaments.find(id));}
    @PostMapping("/matches/{matchId}/override") public Object override(Authentication auth,@PathVariable String id,@PathVariable String matchId,@RequestBody OverrideResult body){admin.override(auth,id,body.revision(),matchId,body.winnerParticipantId(),body.reason());return tournaments.view(tournaments.find(id));}
    @GetMapping("/review") public Object review(Authentication auth,@PathVariable String id){var t=tournaments.find(id);authorization.requireManager(auth,t);return Map.of("tournament",tournaments.view(t),"policy","Execution diagnostics are represented by safe error codes. Replays retain the original game facts; administrative decisions are audited separately.");}
    @GetMapping("/report") public Object report(@PathVariable String id){return tournaments.report(tournaments.find(id));}
    public record Control(Long revision,String reason){}
    public record RoundPolicy(Long revision,boolean autoAdvanceRounds){}
    public record Participant(Long revision,ParticipantStatus status,String reason){}
    public record Review(Long revision,String reason,boolean keepResult){}
    public record OverrideResult(Long revision,String reason,String winnerParticipantId){}
}
