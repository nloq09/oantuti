package com.rpsbattle.tournament.domain;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record TournamentParticipant(String participantId, String botId, @JsonIgnore String botToken, String displayName, long seed) {}
