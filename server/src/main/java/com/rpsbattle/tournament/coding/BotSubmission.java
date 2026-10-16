package com.rpsbattle.tournament.coding;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tournament_bot_submissions")
public class BotSubmission {
 @Id UUID id;
 @Column(name="tournament_id",nullable=false) UUID tournamentId;
 @Column(name="user_id",nullable=false) UUID userId;
 @Column(name="registration_id",nullable=false) UUID registrationId;
 @Column(name="version_id",nullable=false) UUID versionId;
 @Column(name="execution_bot_id",nullable=false) UUID executionBotId;
 @Column(name="artifact_hash",nullable=false,length=64) String artifactHash;
 @Column(name="source_type",nullable=false,length=24) String sourceType;
 @Column(nullable=false,length=24) String status;
 @Column(name="submitted_at",nullable=false) Instant submittedAt;
 @Column(name="locked_at") Instant lockedAt;
 protected BotSubmission(){}
}
