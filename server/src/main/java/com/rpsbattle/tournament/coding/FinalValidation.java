package com.rpsbattle.tournament.coding;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tournament_final_validations")
public class FinalValidation {
 @Id UUID id;
 @Column(name="freeze_id",nullable=false) UUID freezeId;
 @Column(name="tournament_id",nullable=false) UUID tournamentId;
 @Column(name="user_id",nullable=false) UUID userId;
 @Column(name="version_id",nullable=false) UUID versionId;
 @Column(name="artifact_hash",nullable=false,length=64) String artifactHash;
 @Column(name="source_hash",nullable=false,length=64) String sourceHash;
 @Column(nullable=false,length=20) String status;
 @Column(name="failure_policy",nullable=false,length=40) String failurePolicy;
 @Column(name="steps_json",nullable=false,columnDefinition="text") String stepsJson;
 @Column(name="history_json",nullable=false,columnDefinition="text") String historyJson;
 @Column(length=80) String reason;
 @Column(name="job_id") UUID jobId;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="finished_at") Instant finishedAt;
 @Column(name="applied_at") Instant appliedAt;
 @Column(name="emergency_open_at") Instant emergencyOpenAt;
 @Column(name="emergency_deadline") Instant emergencyDeadline;
 @Column(name="emergency_actor") UUID emergencyActor;
 @Column(name="emergency_reason",length=400) String emergencyReason;
 protected FinalValidation(){}
}
