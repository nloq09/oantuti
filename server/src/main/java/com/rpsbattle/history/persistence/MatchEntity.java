package com.rpsbattle.history.persistence;
import com.rpsbattle.history.domain.MatchMetadata;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="matches")
public class MatchEntity {
    @Id @Column(length=64) String id;
    @Column(name="room_id",length=64) String roomId;
    @Column(nullable=false,length=32) String mode;
    @Column(nullable=false,length=32) String status;
    @Column(name="player1_user_id") UUID player1UserId;
    @Column(name="player2_user_id") UUID player2UserId;
    @Column(name="winner_user_id") UUID winnerUserId;
    @Column(name="winner_seat",length=2) String winnerSeat;
    @Column(length=16) String outcome;
    @Column(name="turn_count",nullable=false) int turnCount;
    @Column(name="started_at") Instant startedAt;
    @Column(name="finished_at") Instant finishedAt;
    @Column(name="duration_ms") Long durationMs;
    @Column(name="rule_set",nullable=false,length=80) String ruleSet;
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected MatchEntity() {}
    void copy(MatchMetadata m) {
        id=m.id();roomId=m.roomId();mode=m.mode();status=m.status();player1UserId=m.player1UserId();player2UserId=m.player2UserId();
        winnerUserId=m.winnerUserId();winnerSeat=m.winnerSeat();outcome=m.outcome();turnCount=m.turnCount();startedAt=m.startedAt();
        finishedAt=m.finishedAt();durationMs=m.durationMs();ruleSet=m.ruleSet();createdAt=m.createdAt();
    }
}
