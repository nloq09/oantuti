package com.rpsbattle.worker;

import com.rpsbattle.tournament.domain.Tournament;
import com.rpsbattle.tournament.domain.TournamentMatch;

/** Internal execution context; never serialized to clients (contains bot capabilities). */
public record MatchExecutionRequest(Tournament tournament, TournamentMatch match) {}
