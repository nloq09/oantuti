package com.rpsbattle.tournament;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.domain.UserRole;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.domain.Tournament;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
@Service
public class TournamentAuthorizationService {
    private final AccountAuthorizationService accounts;
    public TournamentAuthorizationService(AccountAuthorizationService accounts) {this.accounts=accounts;}
    public boolean manages(Authentication auth,Tournament tournament) {
        var actor=accounts.requireUser(auth);return actor.role()==UserRole.ADMIN || actor.id().value().equals(tournament.ownerUserId);
    }
    public void requireManager(Authentication auth,Tournament tournament) {if(!manages(auth,tournament)) throw new AuthException("TOURNAMENT_FORBIDDEN",403);}
}
