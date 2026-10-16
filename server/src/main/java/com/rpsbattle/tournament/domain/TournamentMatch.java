package com.rpsbattle.tournament.domain;
import java.time.Instant;
public final class TournamentMatch {
    public String seriesId;
    public int gameNumber=1;
    public int round=1;
    public Instant scheduledAt;
    public long moveNanosA,moveNanosB;
    public int moveCountA,moveCountB;
    public int attempt=1;
    public String decisionReason;
    public TournamentMatch copy() {
        var m=new TournamentMatch(id,tournamentId,participantA,participantB);
        m.seriesId=seriesId;m.gameNumber=gameNumber;m.round=round;m.scheduledAt=scheduledAt;m.moveNanosA=moveNanosA;m.moveNanosB=moveNanosB;m.moveCountA=moveCountA;m.moveCountB=moveCountB;
        m.attempt=attempt;m.decisionReason=decisionReason;m.onlineMatchId=onlineMatchId;m.historyMatchId=historyMatchId;m.status=status;
        m.winnerParticipantId=winnerParticipantId;m.result=result;m.failure=failure;m.startedAt=startedAt;m.finishedAt=finishedAt;m.remainingA=remainingA;m.remainingB=remainingB;return m;
    }
    public void restore(TournamentMatch m){seriesId=m.seriesId;gameNumber=m.gameNumber;round=m.round;scheduledAt=m.scheduledAt;moveNanosA=m.moveNanosA;moveNanosB=m.moveNanosB;moveCountA=m.moveCountA;moveCountB=m.moveCountB;attempt=m.attempt;decisionReason=m.decisionReason;onlineMatchId=m.onlineMatchId;historyMatchId=m.historyMatchId;status=m.status;winnerParticipantId=m.winnerParticipantId;result=m.result;failure=m.failure;startedAt=m.startedAt;finishedAt=m.finishedAt;remainingA=m.remainingA;remainingB=m.remainingB;}
    public enum Status { PENDING, RUNNING, FINISHED, FAILED }
    public final String id, tournamentId;
    public final TournamentParticipant participantA, participantB;
    public String onlineMatchId, winnerParticipantId, result, failure;
    @com.fasterxml.jackson.annotation.JsonIgnore public String historyMatchId;
    public Status status = Status.PENDING;
    public Instant startedAt, finishedAt;
    public int remainingA, remainingB;
    public TournamentMatch(String id, String tournamentId, TournamentParticipant a, TournamentParticipant b) {
        this.id = id; this.seriesId=id; this.tournamentId = tournamentId; this.participantA = a; this.participantB = b;
    }
}
