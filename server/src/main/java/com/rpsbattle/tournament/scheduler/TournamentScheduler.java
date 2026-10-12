package com.rpsbattle.tournament.scheduler;
import com.rpsbattle.tournament.domain.*;
import java.util.List;
public interface TournamentScheduler { List<TournamentMatch> schedule(Tournament tournament); }
