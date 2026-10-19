package com.rpsbattle.api.controller;

import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import java.time.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tournaments/{id}/timeline")
public class TournamentTimelineController {
    private final TournamentService tournaments;
    private final OwnedTournamentService owned;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.timeline.TimelineRegistrationStore applications;
    @org.springframework.beans.factory.annotation.Autowired TimelineOverrideService overrides;
    public TournamentTimelineController(TournamentService tournaments,OwnedTournamentService owned,Clock clock){this.tournaments=tournaments;this.owned=owned;this.clock=clock;}
    public record Configure(Long revision,TournamentTimeline timeline) {}
    public record View(TournamentTimeline timeline,TournamentPhase phase,Instant serverNow,Instant phaseEndsAt,long revision,boolean timelineConfigured) {}
    @GetMapping public ResponseEntity<View> read(@PathVariable String id){
        owned.flush();var t=tournaments.find(id);
        synchronized(t){var now=clock.instant();boolean reviewed=t.registrations.stream().noneMatch(r->r.status()==TournamentRegistration.Status.PENDING)&&(t.timeline==null||applications.reviewComplete(java.util.UUID.fromString(t.id)));
            var result=TournamentPhaseResolver.resolve(t.status,t.timeline,now,null,reviewed);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new View(t.timeline,result.phase(),now,result.phaseEndsAt(),t.revision,t.timeline!=null));}
    }
    @PutMapping public ResponseEntity<View> configure(Authentication auth,@PathVariable String id,@RequestBody Configure request){owned.configureTimeline(auth,id,request.revision(),request.timeline());return read(id);}
    @PostMapping("/override") public ResponseEntity<View> override(Authentication auth,@PathVariable String id,@RequestBody TimelineOverrideService.Change request){overrides.change(auth,id,request);return read(id);}
}
