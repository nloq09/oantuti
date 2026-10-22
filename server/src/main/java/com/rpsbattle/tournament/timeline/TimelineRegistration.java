package com.rpsbattle.tournament.timeline;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Application by a person, deliberately separate from execution roster/bot identity. */
@Entity @Table(name="tournament_timeline_registrations")
public class TimelineRegistration {
    @Id UUID id;
    @Column(name="tournament_id",nullable=false) UUID tournamentId;
    @Column(name="user_id",nullable=false) UUID userId;
    @Column(name="display_name",nullable=false,length=80) String displayName;
    @Column(name="intended_language",nullable=false,length=16) String intendedLanguage;
    @Column(length=80) String affiliation;
    @Column(nullable=false,length=16) String status;
    @Column(name="registered_at",nullable=false) Instant registeredAt;
    @Column(name="rules_accepted_at",nullable=false) Instant rulesAcceptedAt;
    @Column(name="review_reason",length=500) String reviewReason;
    @Column(name="public_review_message",length=500) String publicReviewMessage;
    @Column(name="reviewed_at") Instant reviewedAt;
    @Column(name="reviewed_by") UUID reviewedBy;
    @Column(name="eligibility_reasons",nullable=false,length=500) String eligibilityReasons="";
    @Column(name="waitlist_order") Long waitlistOrder;
    protected TimelineRegistration() {}
}
