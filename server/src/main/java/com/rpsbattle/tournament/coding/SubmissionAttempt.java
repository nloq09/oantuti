package com.rpsbattle.tournament.coding;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A durable request identity and immutable version, separate from the editable draft. */
@Entity @Table(name="tournament_submission_attempts")
public class SubmissionAttempt {
 @Id UUID id;
 @Column(name="tournament_id",nullable=false) UUID tournamentId;
 @Column(name="user_id",nullable=false) UUID userId;
 @Column(name="version_id",nullable=false) UUID versionId;
 @Column(nullable=false,length=24) String status;
 @Column(name="error_code",length=64) String errorCode;
 @Column(name="error_detail",columnDefinition="text") String errorDetail;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="updated_at",nullable=false) Instant updatedAt;
 @Column(name="finished_at") Instant finishedAt;
 protected SubmissionAttempt(){}
}
