package com.rpsbattle.tournament.domain;
import java.util.List;
/** A rest is part of the schedule, never a played match or a scored win. */
public record TournamentRound(int number, List<String> byeParticipantIds) {
    public TournamentRound { byeParticipantIds=List.copyOf(byeParticipantIds); }
}
