package com.rpsbattle.history;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class UserStatsService {
    private final MatchHistoryRepository repository;
    public UserStatsService(MatchHistoryRepository repository) {this.repository=repository;}
    public Stats account(UUID owner) {
        var totals=repository.totals(owner);
        double rate=totals.played()==0?0:Math.round(10000.0*totals.wins()/totals.played())/100.0;
        return new Stats(totals.played(),totals.wins(),totals.draws(),totals.played()-totals.wins()-totals.draws(),rate,totals.totalTurns());
    }
    public record Stats(long matchesPlayed,long wins,long draws,long losses,double winRate,long totalTurns) {}
}
