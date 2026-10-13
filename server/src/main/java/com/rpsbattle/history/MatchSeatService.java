package com.rpsbattle.history;
import com.rpsbattle.auth.AuthService;
import com.rpsbattle.bot.ownership.BotRepository;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.online.OnlineMatch;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class MatchSeatService {
    private final AuthService accounts;
    private final BotRepository bots;
    public MatchSeatService(AuthService accounts,BotRepository bots) {this.accounts=accounts;this.bots=bots;}
    public void bind(OnlineMatch room,PlayerId seat,Authentication authentication,String botId) {
        var owner=accounts.current(authentication).map(u->u.id().value()).orElse(null);
        room.bindSeat(seat,owner,botId==null?null:UUID.fromString(botId));
    }
    public void bindTournamentBot(OnlineMatch room,PlayerId seat,String botId) {
        var id=UUID.fromString(botId);var owner=bots.find(id).map(b->b.ownerUserId()).orElse(null);
        // Legacy internal tournament fixtures can have an unclaimed artifact: no account statistics.
        room.bindSeat(seat,owner,owner==null?null:id);
    }
}
