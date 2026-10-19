package com.rpsbattle.tournament.timeline;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.TournamentPhaseResolver;
import com.rpsbattle.tournament.domain.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TimelineAccessGuard {
    private final TimelineRegistrationStore store;
    private final Clock clock;
    public TimelineAccessGuard(TimelineRegistrationStore store,Clock clock){this.store=store;this.clock=clock;}
    /** Legacy tournaments keep their existing policy; scheduled applications require approval. */
    public void coding(Tournament tournament,UUID user){
        if(tournament.timeline==null)return;
        var phase=TournamentPhaseResolver.resolve(tournament.status,tournament.timeline,clock.instant(),null,false).phase();
        if(phase!=TournamentPhase.CODING_OPEN)throw new AuthException("CODING_NOT_OPEN",409);
        var application=store.mine(UUID.fromString(tournament.id),user);
        if(application==null||!application.status().equals("APPROVED"))throw new AuthException("REGISTRATION_NOT_APPROVED",403);
    }
}
