package com.rpsbattle.tournament.domain;
import java.time.Instant;
import java.util.*;
/** Mutation and consistent snapshots are guarded by the tournament monitor. */
public final class Tournament {
    public final String id, name;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public final String ownerToken = UUID.randomUUID().toString();
    public final TournamentFormat format;
    public UUID ownerUserId;
    @com.fasterxml.jackson.annotation.JsonIgnore public boolean deferNotifications;
    public TournamentStatus status = TournamentStatus.DRAFT;
    public final List<TournamentParticipant> participants = new ArrayList<>();
    public final List<TournamentMatch> matches = new ArrayList<>();
    public final List<TournamentRound> rounds = new ArrayList<>();
    public final List<BracketNode> bracket = new ArrayList<>();
    public final List<TournamentStage> stages = new ArrayList<>();
    public TournamentOperations operations=TournamentOperations.defaults();
    public int maxConcurrentMatches=4;
    public List<com.rpsbattle.tournament.ranking.TieBreak> tieBreakOrder=List.of(
        com.rpsbattle.tournament.ranking.TieBreak.POINTS,com.rpsbattle.tournament.ranking.TieBreak.WINS,
        com.rpsbattle.tournament.ranking.TieBreak.HEAD_TO_HEAD,com.rpsbattle.tournament.ranking.TieBreak.REMAINING_PIECES);
    public final Instant createdAt;
    public Instant startedAt, finishedAt, statusChangedAt;
    public long revision;
    public boolean checkInRequired;
    public ParticipantPolicy participantPolicy=ParticipantPolicy.BOT_ONLY;
    public TournamentTiming timing=TournamentTiming.manual();
    public final Map<String,MatchReadyWindow> readyWindows=new LinkedHashMap<>();
    public final Map<String,Long> notificationReads=new LinkedHashMap<>();
    public int maxParticipants=10;
    public SeedingStrategy seedingStrategy=SeedingStrategy.RANDOM;
    public long seedingSeed=java.util.UUID.randomUUID().getMostSignificantBits();
    public final List<TournamentRegistration> registrations=new ArrayList<>();
    public Tournament(String id, String name, TournamentFormat format, Instant createdAt) {
        this.id=id; this.name=name; this.format=format; this.createdAt=createdAt;this.statusChangedAt=createdAt;
        stages.add(new TournamentStage(id+"/stage/1",1,format,null));
    }
}
