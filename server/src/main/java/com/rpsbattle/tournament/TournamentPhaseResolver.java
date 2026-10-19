package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import java.time.Instant;
import java.util.Objects;

/** Pure resolver: reads server time on every request; never starts matches or fabricates results. */
public final class TournamentPhaseResolver {
    private TournamentPhaseResolver() {}
    public record Resolution(TournamentPhase phase, Instant phaseEndsAt) {}

    public static Resolution resolve(TournamentStatus status, TournamentTimeline timeline,
            Instant serverNow, TournamentPhase override, boolean reviewComplete) {
        Objects.requireNonNull(status); Objects.requireNonNull(serverNow);
        switch (status) {
            case DRAFT: return at(TournamentPhase.DRAFT, null);
            case CANCELLED: return at(TournamentPhase.CANCELLED, null);
            case FINISHED: return at(TournamentPhase.FINISHED, null);
            case RUNNING: return at(TournamentPhase.RUNNING, null);
            case PAUSED: return at(TournamentPhase.PAUSED, null);
            case RECOVERY_REQUIRED: return at(TournamentPhase.RECOVERY_REQUIRED, null);
            default: break;
        }
        // Administrative overrides cannot pretend the engine has started/finished a tournament.
        if (override != null) {
            if (java.util.Set.of(TournamentPhase.DRAFT, TournamentPhase.RUNNING, TournamentPhase.FINISHED,
                    TournamentPhase.CANCELLED, TournamentPhase.PAUSED, TournamentPhase.RECOVERY_REQUIRED).contains(override))
                throw new IllegalArgumentException("Lifecycle phases require a lifecycle transition");
            return at(override, null);
        }
        if (timeline == null) return at(switch(status) {
            case REGISTRATION -> TournamentPhase.REGISTRATION_OPEN;
            case CHECK_IN -> TournamentPhase.CHECK_IN_OPEN;
            default -> TournamentPhase.READY_TO_START;
        }, null);
        if (serverNow.isBefore(timeline.registrationOpenAt())) return at(TournamentPhase.UPCOMING, timeline.registrationOpenAt());
        if (serverNow.isBefore(timeline.registrationCloseAt())) return at(TournamentPhase.REGISTRATION_OPEN, timeline.registrationCloseAt());
        if (serverNow.isBefore(timeline.codingOpenAt())) return at(reviewComplete ? TournamentPhase.WAITING_FOR_CODING : TournamentPhase.REGISTRATION_REVIEW, timeline.codingOpenAt());
        if (serverNow.isBefore(timeline.codingCloseAt())) return at(TournamentPhase.CODING_OPEN, timeline.codingCloseAt());
        if (serverNow.isBefore(timeline.validationStartAt())) return at(TournamentPhase.SUBMISSION_FROZEN, timeline.validationStartAt());
        if (serverNow.isBefore(timeline.checkInOpenAt())) return at(TournamentPhase.VALIDATING, timeline.checkInOpenAt());
        if (serverNow.isBefore(timeline.checkInCloseAt())) return at(TournamentPhase.CHECK_IN_OPEN, timeline.checkInCloseAt());
        // Reaching start time does not imply fixtures were created or that validation succeeded.
        return at(TournamentPhase.READY_TO_START, timeline.tournamentStartAt());
    }
    private static Resolution at(TournamentPhase phase, Instant end) { return new Resolution(phase, end); }
}
