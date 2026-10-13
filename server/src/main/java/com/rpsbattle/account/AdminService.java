package com.rpsbattle.account;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.MatchRegistry;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.domain.TournamentStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

@Service
public class AdminService {
    private final UserRepository users;
    private final AccountAuthorizationService authorization;
    private final MatchRegistry matches;
    private final TournamentRegistry tournaments;
    private com.rpsbattle.worker.WorkerManager workers;
    @org.springframework.beans.factory.annotation.Autowired
    public void workers(com.rpsbattle.worker.WorkerManager workers){this.workers=workers;}
    public AdminService(UserRepository users,AccountAuthorizationService authorization,MatchRegistry matches,TournamentRegistry tournaments) {
        this.users=users;this.authorization=authorization;this.matches=matches;this.tournaments=tournaments;
    }
    public UserRepository.Page list(Authentication authentication,int page,int size) {
        authorization.requireAdmin(authentication);
        if(page<0 || page>100000 || size<1 || size>100) throw new AuthException("INVALID_PAGE",400);
        return users.findAll(page,size);
    }
    @Transactional public User setStatus(Authentication authentication,UserId id,AccountStatus status) {
        var actor=authorization.requireAdmin(authentication);
        if(actor.id().equals(id) && status==AccountStatus.DISABLED) throw new AuthException("CANNOT_DISABLE_SELF",400);
        var target=users.findByIdForUpdate(id).orElseThrow(()->new AuthException("USER_NOT_FOUND",404));
        if(target.status()==status) return target;
        return users.save(target.withStatus(status,Instant.now()));
    }
    public Map<String,Object> system(Authentication authentication) {
        authorization.requireAdmin(authentication);
        return Map.of("users",users.count(),"activeMatches",matches.getActiveMatches().stream().filter(m->!m.state().isFinished()).count(),
                "runningTournaments",tournaments.findAll().stream().filter(t->{synchronized(t){return t.status==TournamentStatus.RUNNING;}}).count(),"workers",workers==null?Map.of():workers.status(),"checkedAt",Instant.now());
    }
}
