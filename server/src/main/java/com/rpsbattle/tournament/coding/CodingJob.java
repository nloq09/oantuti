package com.rpsbattle.tournament.coding;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="bot_coding_jobs")
public class CodingJob {
 @Id UUID id;
 @Column(name="workspace_id",nullable=false) UUID workspaceId;
 @Column(name="version_id",nullable=false) UUID versionId;
 @Column(nullable=false,length=16) String kind;
 @Column(nullable=false,length=16) String status;
 @Column(name="lease_token") UUID leaseToken;
 @Column(name="lease_until") Instant leaseUntil;
 @Column(nullable=false) int attempts;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="finished_at") Instant finishedAt;
 @Column(name="hidden_required",nullable=false) boolean hiddenRequired;
 @Column(name="final_validation_id") UUID finalValidationId;
 protected CodingJob(){}
}
