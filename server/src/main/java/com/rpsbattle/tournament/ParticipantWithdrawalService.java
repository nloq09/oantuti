package com.rpsbattle.tournament;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.Set;

/** Commit intent first. The execution loop then surrenders any live game using its ordinary flow. */
@Service
public class ParticipantWithdrawalService {
    private final TournamentService tournaments;
    private final OwnedTournamentService owned;
    private final AccountAuthorizationService accounts;
    public ParticipantWithdrawalService(TournamentService tournaments, OwnedTournamentService owned, AccountAuthorizationService accounts) {
        this.tournaments=tournaments; this.owned=owned; this.accounts=accounts;
    }
    public void withdraw(Authentication auth,String id,Long revision,String participantId,boolean confirmed) {
        var actor=accounts.requireUser(auth).id().value();var t=tournaments.find(id);
        synchronized(t) {
            var participant=t.participants.stream().filter(p->p.participantId().equals(participantId)&&actor.equals(p.ownerUserId())).findFirst()
                .orElseThrow(()->new AuthException("TOURNAMENT_FORBIDDEN",403));
            if(revision==null||revision!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);
            if(!confirmed)throw new AuthException("WITHDRAW_CONFIRMATION_REQUIRED",400);
            if(!Set.of(TournamentStatus.READY,TournamentStatus.RUNNING,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED).contains(t.status))throw new AuthException("TOURNAMENT_LOCKED",409);
            if(!t.operations.active(participant.participantId()))return;
            owned.systemMutation(t,()->{
                t.operations=t.operations.participant(participantId,ParticipantStatus.WITHDRAWN);
                t.readyWindows.entrySet().removeIf(e->t.matches.stream().anyMatch(m->m.id.equals(e.getKey())&&(participantId.equals(m.participantA.participantId())||participantId.equals(m.participantB.participantId()))));
                TournamentAudit.append(t,"PARTICIPANT_WITHDRAWN",actor,participantId,"SELF_WITHDRAWAL","ACTIVE","WITHDRAWN");
                TournamentAdministrationService.settleForfeits(t);tournaments.changed(t);
            });
        }
    }
}
