package com.rpsbattle.tournament.timeline;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.TournamentPhaseResolver;
import com.rpsbattle.tournament.domain.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TimelineAccessGuard {
    @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.coding.FinalValidationStore finals;
    private final TimelineRegistrationStore store;
    private final Clock clock;
    public TimelineAccessGuard(TimelineRegistrationStore store,Clock clock){this.store=store;this.clock=clock;}
    /** Legacy tournaments keep their existing policy; scheduled applications require approval. */
    public void coding(Tournament tournament,UUID user){
        if(tournament.timeline==null)return;
        var phase=TournamentPhaseResolver.resolve(tournament.status,tournament.timeline,clock.instant(),null,false).phase();
        if(phase!=TournamentPhase.CODING_OPEN&&!(tournament.startedAt==null&&tournament.status==TournamentStatus.REGISTRATION&&finals.emergencyOpen(tournament.id,user)))throw new AuthException("CODING_NOT_OPEN",409);
        var application=store.mine(UUID.fromString(tournament.id),user);
        if(application==null||!application.status().equals("APPROVED"))throw new AuthException("REGISTRATION_NOT_APPROVED",403);
    }
    public int versionLimit(Tournament t,UUID user){return t.botPolicy.maxSubmissionVersions()+(int)Math.min(300,t.operations.audit().stream().filter(a->a.type().equals("TIMELINE_OVERRIDE_EXTEND_CODING")).count()*3)+(t.timeline!=null?finals.emergencyGrantCount(t.id,user)*3:0);}
    public void readable(Tournament tournament,UUID user){
        if(tournament.timeline==null)return;
        if(tournament.status==TournamentStatus.DRAFT||clock.instant().isBefore(tournament.timeline.codingOpenAt()))throw new AuthException("CODING_NOT_OPEN",409);
        var application=store.mine(UUID.fromString(tournament.id),user);
        if(application==null||!application.status().equals("APPROVED"))throw new AuthException("REGISTRATION_NOT_APPROVED",403);
    }
}
