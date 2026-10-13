package com.rpsbattle.api.controller;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.history.*;
import com.rpsbattle.history.domain.MatchMetadata;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/account")
public class AccountHistoryController {
    private final AccountAuthorizationService authorization;
    private final MatchHistoryRepository matches;
    private final MatchPersistenceCoordinator persistence;
    private final UserStatsService stats;
    public AccountHistoryController(AccountAuthorizationService authorization,MatchHistoryRepository matches,MatchPersistenceCoordinator persistence,UserStatsService stats) {this.authorization=authorization;this.matches=matches;this.persistence=persistence;this.stats=stats;}
    @GetMapping("/matches") public ResponseEntity<Map<String,Object>> history(Authentication auth,
            @RequestParam(defaultValue="all") String filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var owner=authorization.requireUser(auth).id().value();
        if(page<0 || page>100000 || size<1 || size>100 || !Set.of("all","win","loss","draw","human","bot").contains(filter)) throw new AuthException("INVALID_HISTORY_QUERY",400);
        persistence.flushForRead();var result=matches.history(owner,filter,page,size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("matches",result.matches().stream().map(m->View.of(m,owner)).toList(),"total",result.total(),"page",page,"size",size));
    }
    @GetMapping("/matches/{id}") public ResponseEntity<View> detail(Authentication auth,@PathVariable String id) {
        var owner=authorization.requireUser(auth).id().value();persistence.flushForRead();
        var match=matches.find(id).filter(m->owner.equals(m.player1UserId()) || owner.equals(m.player2UserId())).orElseThrow(()->new AuthException("MATCH_HISTORY_NOT_FOUND",404));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(View.of(match,owner));
    }
    @GetMapping("/stats") public ResponseEntity<UserStatsService.Stats> stats(Authentication auth) {
        var owner=authorization.requireUser(auth).id().value();persistence.flushForRead();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stats.account(owner));
    }
    public record View(String id,String roomId,String mode,String status,String outcome,String winnerSeat,int turnCount,
            Instant startedAt,Instant finishedAt,Long durationMs,String ruleSet,Instant createdAt,List<MatchMetadata.Participant> participants) {
        static View of(MatchMetadata m,UUID owner) {return new View(m.id(),m.roomId(),m.mode(),m.status(),m.resultFor(owner),m.winnerSeat(),m.turnCount(),m.startedAt(),m.finishedAt(),m.durationMs(),m.ruleSet(),m.createdAt(),m.participants());}
    }
}
