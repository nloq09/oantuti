package com.rpsbattle.tournament.coding;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable deadline decision, separate from subsequent final validation outcomes. */
@Entity @Table(name="tournament_coding_freezes")
public class CodingFreeze {
 @Id UUID id;
 @Column(name="tournament_id",nullable=false) UUID tournamentId;
 @Column(name="user_id",nullable=false) UUID userId;
 @Column(name="coding_close_at",nullable=false) Instant codingCloseAt;
 @Column(nullable=false,length=16) String decision;
 @Column(length=80) String reason;
 @Column(name="version_id") UUID versionId;
 @Column(name="version_number") Integer versionNumber;
 @Column(length=16) String language;
 @Column(name="source_hash",length=64) String sourceHash;
 @Column(name="artifact_hash",length=64) String artifactHash;
 @Column(name="submitted_at") Instant submittedAt;
 @Column(name="frozen_at",nullable=false) Instant frozenAt;
 protected CodingFreeze(){}
 public record View(String decision,String reason,UUID versionId,Integer version,String language,String sourceHash,String artifactHash,Instant submittedAt,Instant codingCloseAt,Instant frozenAt){}
 View view(){return new View(decision,reason,versionId,versionNumber,language,sourceHash,artifactHash,submittedAt,codingCloseAt,frozenAt);}
}
