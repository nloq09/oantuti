package com.rpsbattle.tournament.domain;

import java.time.Instant;
import com.rpsbattle.online.DemoRequestException;

/** Complete schedule. Null timeline on Tournament denotes the legacy manual flow. */
public record TournamentTimeline(Instant registrationOpenAt, Instant registrationCloseAt,
        Instant codingOpenAt, Instant codingCloseAt, Instant validationStartAt,
        Instant validationDeadline, Instant checkInOpenAt, Instant checkInCloseAt,
        Instant tournamentStartAt, Instant estimatedEndAt, Instant resultPublishAt, DeadlinePolicy deadlinePolicy) {
    public enum DeadlinePolicy { REQUIRE_EXPLICIT_SUBMIT, AUTO_SUBMIT_LATEST_READY }
    public TournamentTimeline(Instant registrationOpenAt,Instant registrationCloseAt,Instant codingOpenAt,Instant codingCloseAt,Instant validationStartAt,Instant validationDeadline,Instant checkInOpenAt,Instant checkInCloseAt,Instant tournamentStartAt,Instant estimatedEndAt,Instant resultPublishAt){this(registrationOpenAt,registrationCloseAt,codingOpenAt,codingCloseAt,validationStartAt,validationDeadline,checkInOpenAt,checkInCloseAt,tournamentStartAt,estimatedEndAt,resultPublishAt,null);}
    public TournamentTimeline {
        deadlinePolicy=deadlinePolicy==null?DeadlinePolicy.AUTO_SUBMIT_LATEST_READY:deadlinePolicy;
        if (registrationOpenAt == null || registrationCloseAt == null || codingOpenAt == null
                || codingCloseAt == null || validationStartAt == null || validationDeadline == null
                || checkInOpenAt == null || checkInCloseAt == null || tournamentStartAt == null
                || estimatedEndAt == null)
            throw new DemoRequestException("INVALID_TOURNAMENT_TIMELINE");
        if (!registrationOpenAt.isBefore(registrationCloseAt)
                || registrationCloseAt.isAfter(codingOpenAt) || !codingOpenAt.isBefore(codingCloseAt)
                || codingCloseAt.isAfter(validationStartAt) || validationStartAt.isAfter(validationDeadline)
                || validationDeadline.isAfter(checkInOpenAt) || !checkInOpenAt.isBefore(checkInCloseAt)
                || checkInCloseAt.isAfter(tournamentStartAt) || tournamentStartAt.isAfter(estimatedEndAt)
                || resultPublishAt != null && resultPublishAt.isBefore(estimatedEndAt))
            throw new DemoRequestException("INVALID_TOURNAMENT_TIMELINE");
    }
}
