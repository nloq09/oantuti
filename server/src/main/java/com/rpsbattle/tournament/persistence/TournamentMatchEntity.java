package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.TournamentMatch;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tournament_matches")
public class TournamentMatchEntity {
    @Id UUID id;
    @Column(name="tournament_id",nullable=false) UUID tournamentId;
    @Column(name="series_id") UUID seriesId;
    @Column(name="game_number",nullable=false) int gameNumber=1;
    @Column(name="match_id",length=64) String historyId;
    @Column(name="participant_a_id",nullable=false) UUID a;
    @Column(name="participant_b_id",nullable=false) UUID b;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) TournamentMatch.Status status;
    @Column(name="winner_participant_id") UUID winner;
    @Column(name="outcome",length=16) String result;
    @Column(length=80) String failure;
    @Column(name="remaining_a") Integer remainingA;
    @Column(name="remaining_b") Integer remainingB;
    @Column(name="started_at") Instant startedAt;
    @Column(name="finished_at") Instant finishedAt;
    protected TournamentMatchEntity() {}
    void copy(UUID tournament,TournamentMetadata.Entry e) {id=e.id();tournamentId=tournament;historyId=e.historyId();a=e.a();b=e.b();status=e.status();winner=e.winner();result=e.result();failure=e.failure();remainingA=e.remainingA();remainingB=e.remainingB();startedAt=e.startedAt();finishedAt=e.finishedAt();}
}
