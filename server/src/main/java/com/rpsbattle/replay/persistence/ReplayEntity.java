package com.rpsbattle.replay.persistence;
import com.rpsbattle.replay.ReplayMetadata;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="replays")
public class ReplayEntity {
    @Id UUID id;
    @Column(name="match_id",nullable=false,length=64,unique=true) String matchId;
    @Column(name="file_path",nullable=false,length=1024) String filePath;
    @Column(name="event_count",nullable=false) int eventCount;
    @Column(nullable=false) boolean valid;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(length=64) String sha256;
    protected ReplayEntity() {}
    ReplayEntity(ReplayMetadata r) {id=r.id();matchId=r.matchId();filePath=r.filePath();eventCount=r.eventCount();valid=r.valid();createdAt=r.createdAt();sha256=r.sha256();}
    ReplayMetadata domain() {return new ReplayMetadata(id,matchId,filePath,eventCount,valid,createdAt,sha256);}
}
