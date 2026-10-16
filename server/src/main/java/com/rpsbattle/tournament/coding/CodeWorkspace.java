package com.rpsbattle.tournament.coding;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Private source is never serialized through tournament/public DTOs. */
@Entity @Table(name="tournament_code_workspaces")
public class CodeWorkspace {
 @Id UUID id;
 @Column(name="tournament_id",nullable=false) UUID tournamentId;
 @Column(name="user_id",nullable=false) UUID userId;
 @Column(nullable=false,length=16) String language;
 @Column(name="source_code",nullable=false,columnDefinition="text") String source;
 @Column(nullable=false) long revision;
 @Column(nullable=false,length=24) String status;
 @Column(name="current_version_id") UUID currentVersionId;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="updated_at",nullable=false) Instant updatedAt;
 protected CodeWorkspace(){}
 boolean busy(){return status.equals("BUILDING")||status.equals("TESTING");}
 void touch(){revision++;updatedAt=Instant.now();}
}
