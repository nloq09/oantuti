package com.rpsbattle.tournament.domain;
import java.time.Instant;
public final class TournamentMatch {
    public enum Status { PENDING, RUNNING, FINISHED, FAILED }
    public final String id, tournamentId;
    public final TournamentParticipant participantA, participantB;
    public String onlineMatchId, winnerParticipantId, result, failure;
    @com.fasterxml.jackson.annotation.JsonIgnore public String historyMatchId;
    public Status status = Status.PENDING;
    public Instant startedAt, finishedAt;
    public int remainingA, remainingB;
    public TournamentMatch(String id, String tournamentId, TournamentParticipant a, TournamentParticipant b) {
        this.id = id; this.tournamentId = tournamentId; this.participantA = a; this.participantB = b;
    }
}
