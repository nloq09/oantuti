package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import org.springframework.stereotype.Component;
import java.util.*;
/** Format routing stays outside controllers and permits adding another pairing strategy. */
@Component
public class FormatTournamentScheduler implements TournamentScheduler {
    private final Map<TournamentFormat,TournamentScheduler> strategies=Map.of(
        TournamentFormat.ROUND_ROBIN,new RoundRobinScheduler(),
        TournamentFormat.SINGLE_ELIMINATION,new SingleEliminationScheduler(),
        TournamentFormat.GROUP_KNOCKOUT,new GroupKnockoutScheduler(),
        TournamentFormat.SWISS,new SwissScheduler(),TournamentFormat.TEAM_ROUND_ROBIN,new TeamScheduler());
    public List<TournamentMatch> schedule(Tournament tournament) {return strategies.get(tournament.format).schedule(tournament);}
}
