package com.rpsbattle.tournament.coding;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Build source is immutable. Only execution facts are updated by the worker. */
@Entity @Table(name="tournament_submission_versions")
public class CodeVersion {
 @Id UUID id;
 @Column(name="workspace_id",nullable=false) UUID workspaceId;
 @Column(name="version_number",nullable=false) int number;
 @Column(name="source_code",nullable=false,columnDefinition="text") String source;
 @Column(name="source_hash",nullable=false,length=64) String sourceHash;
 @Column(name="artifact_hash",length=64) String artifactHash;
 @Column(name="artifact_id") UUID artifactId;
 @Column(nullable=false,length=24) String status;
 @Column(name="build_log",nullable=false,columnDefinition="text") String buildLog;
 @Column(name="test_result",columnDefinition="text") String testResult;
 @Column(name="sample_result",columnDefinition="text") String sampleResult;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="updated_at",nullable=false) Instant updatedAt;
 protected CodeVersion(){}
}
