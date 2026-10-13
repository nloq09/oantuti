package com.rpsbattle.history.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
interface JpaMatchRepository extends JpaRepository<MatchEntity,String>, JpaSpecificationExecutor<MatchEntity> {
    @Query("""
        select count(m), coalesce(sum(case when m.outcome='WIN' and m.winnerUserId=:owner then 1 else 0 end),0),
            coalesce(sum(case when m.outcome='DRAW' then 1 else 0 end),0), coalesce(sum(m.turnCount),0)
        from MatchEntity m where m.status='FINISHED' and (m.player1UserId=:owner or m.player2UserId=:owner)
            and (m.player1UserId is null or m.player2UserId is null or m.player1UserId<>m.player2UserId)
            and (m.outcome='DRAW' or (m.outcome='WIN' and m.winnerSeat is not null))
        """) List<Object[]> aggregate(@Param("owner") UUID owner);
}
