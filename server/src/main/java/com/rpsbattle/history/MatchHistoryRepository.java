package com.rpsbattle.history;
import com.rpsbattle.history.domain.MatchMetadata;
import java.util.*;
public interface MatchHistoryRepository {
    MatchMetadata save(MatchMetadata metadata);
    Optional<MatchMetadata> find(String id);
    Page history(UUID owner, String filter, int page, int size);
    Totals totals(UUID owner);
    record Page(List<MatchMetadata> matches, long total, int page, int size) {public Page {matches=List.copyOf(matches);}}
    record Totals(long played, long wins, long draws, long totalTurns) {}
}
