package com.rpsbattle.replay;
import com.rpsbattle.history.domain.MatchMetadata;
import java.util.Optional;
public interface ReplayRepository {
    void save(ReplayMetadata replay, MatchMetadata match);
    Optional<ReplayMetadata> find(String archiveOrRoomId);
    void invalidate(String matchId);
}
