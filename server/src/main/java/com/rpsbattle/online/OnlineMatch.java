package com.rpsbattle.online;

import com.rpsbattle.agent.*;
import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.engine.*;
import com.rpsbattle.game.piece.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.*;
import com.rpsbattle.match.Match;
import com.rpsbattle.rules.online.OnlineRuleSet;

import java.time.*;
import java.util.*;

/**
 * In-memory room lifecycle. Rule/engine validate placement and all gameplay.
 */
public final class OnlineMatch {
    public static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    private final String id;
    private final String archiveId = UUID.randomUUID().toString();
    private final Instant createdAt;
    private final Map<PlayerId, SeatIdentity> identities = new HashMap<>();
    private final MatchMode mode;
    private final long seed;
    private final Clock clock;
    private final Duration turnTime;
    private final OnlineRuleSet rules = new OnlineRuleSet(P1, P2);
    private final Map<PlayerId, Seat> seats = new HashMap<>();
    private final Set<PlayerId> connected = new HashSet<>(), ready = new HashSet<>();
    private final Map<PlayerId, List<Piece>> drafts = new HashMap<>();
    private final Map<PlayerId, PlayerAgent> uploadedAgents = new HashMap<>();
    private final Map<PlayerId, String> botNames = new HashMap<>();
    private final LegalActionGenerator generator = new LegalActionGenerator(rules);
    private String botError;
    private Instant nextBotAt = Instant.MIN;
    private GameState setup;
    private Match match;
    private Instant deadline, startedAt, finishedAt;
    private long revision;
    private long botDelayMillis = 600;
    private String infrastructureError;
    public synchronized String infrastructureError() { return infrastructureError; }
    public synchronized void infrastructureFailure() { infrastructureError="MATCH_INFRASTRUCTURE_ERROR"; if(finishedAt==null) finishedAt=clock.instant(); }
    public synchronized void setBotDelayMillis(long millis) {
        if(millis<0||millis>5000)throw new IllegalArgumentException("Invalid bot pacing");
        botDelayMillis=millis;
    }
    public OnlineMatch(String id, MatchMode mode, long seed, Clock clock, Duration turnTime) {
        this.id = id;
        this.mode = mode;
        this.seed = seed;
        this.clock = clock;
        this.createdAt = clock.instant();
        this.turnTime = turnTime;
        setup = GameState.initial(id, Board.defaultBoard(), List.of(Player.of(P1, "Player 1"), Player.of(P2, "Player 2")));
        drafts.put(P1, new ArrayList<>());
        drafts.put(P2, new ArrayList<>());
        seats.put(P1, new Seat(P1, UUID.randomUUID().toString(), false));
        if (mode == MatchMode.HUMAN_VS_BOT) {
            seats.put(P2, new Seat(P2, "", true));
            connected.add(P2);
            setup = setup.withPhase(GamePhase.PLACEMENT);
            randomize(P2);
            ready.add(P2);
        }
    }

    private static void fail(String code) {
        throw new DemoRequestException(code);
    }

    public String id() {
        return id;
    }
    public String archiveId() {return archiveId;}
    public Instant createdAt() {return createdAt;}
    public String ruleSetName() {return rules.name();}
    public synchronized void bindSeat(PlayerId player, UUID userId, UUID botId) {
        if(!seats.containsKey(player)) fail("PLAYER_NOT_FOUND");
        var identity=new SeatIdentity(userId,botId);
        if(identities.containsKey(player) && !identities.get(player).equals(identity)) throw new IllegalStateException("Seat identity is immutable until the seat is released");
        identities.put(player,identity);
    }
    public synchronized SeatIdentity seatIdentity(PlayerId player) {return identities.getOrDefault(player,new SeatIdentity(null,null));}
    public record SeatIdentity(UUID userId, UUID botId) {}

    public MatchMode mode() {
        return mode;
    }

    public long seed() {
        return seed;
    }

    public synchronized Seat creator() {
        return seats.get(P1);
    }

    public synchronized String status() {
        return match != null ? match.state().phase().name() : seats.size() < 2 ? "WAITING" : "PLACEMENT";
    }

    public synchronized Seat join() {
        if (match != null || seats.size() == 2) fail("MATCH_FULL");
        PlayerId player = seats.containsKey(P1) ? P2 : P1;
        var seat = new Seat(player, UUID.randomUUID().toString(), false);
        seats.put(player, seat);
        if (seats.size() == 2) setup = setup.withPhase(GamePhase.PLACEMENT);
        revision++;
        return seat;
    }

    public synchronized void authorize(PlayerId player, String token) {
        var seat = seats.get(player);
        if (seat == null || seat.token().isBlank() || token == null || !seat.token().equals(token))
            fail("PLAYER_NOT_FOUND");
    }

    public synchronized List<Seat> seats() {
        return List.copyOf(seats.values());
    }

    public synchronized String botName(PlayerId player) {
        return botNames.get(player);
    }

    public synchronized String botError() {
        return botError;
    }

    public synchronized boolean uploadedBot(PlayerId player) {
        return uploadedAgents.containsKey(player);
    }

    public synchronized void attachBot(PlayerId player, PlayerAgent agent, String name) {
        if (match != null) fail("INVALID_PHASE");
        var seat = seats.get(player);
        if (seat == null) fail("PLAYER_NOT_FOUND");
        if(agent instanceof com.rpsbattle.bot.BotAgent bot)bot.bindMatch(archiveId());
        uploadedAgents.put(player, agent);
        botNames.put(player, name);
        seats.put(player, new Seat(player, seat.token(), true));
        revision++;
    }

    public synchronized List<GameEvent> prepareBots() {
        List<GameEvent> events = List.of();
        if (!status().equals("PLACEMENT")) return events;
        for (var player : List.of(P1, P2))
            if (uploadedAgents.containsKey(player) && !ready.contains(player)) {
                randomize(player);
                events = markReady(player);
            }
        return events;
    }

    public synchronized Optional<BotTurn> pendingBotTurn() {
        if (match == null || match.state().isFinished() || clock.instant().isBefore(nextBotAt)) return Optional.empty();
        var player = match.state().currentPlayerId();
        var agent = uploadedAgents.get(player);
        if (agent == null) return Optional.empty();
        var actions = new ArrayList<Action>(generator.getLegalActions(match.state(), player));
        if (actions.isEmpty()) actions.add(PassAction.of(player));
        return Optional.of(new BotTurn(player, match.state().sequence(), agent, match.state(), List.copyOf(actions)));
    }

    public synchronized List<GameEvent> completeBot(BotTurn turn, Action action, boolean failed) {
        if (match == null || match.state().isFinished() || match.state().sequence() != turn.sequence())
            return List.of();
        try {
            if (failed || action == null || !turn.actions().contains(action)) throw new IllegalArgumentException();
            return applySubmitted(turn.player(), turn.sequence(), action);
        } catch (RuntimeException error) {
            botError = "BOT_FAILED";
            return submit(turn.player(), turn.sequence(), SurrenderAction.of(turn.player()));
        }
    }

    public synchronized List<PlayerAgent> uploadedAgents() {
        return List.copyOf(uploadedAgents.values());
    }

    public synchronized void connection(PlayerId player, boolean value) {
        if (value) connected.add(player);
        else connected.remove(player);
    }

    public synchronized boolean connected(PlayerId player) {
        return connected.contains(player);
    }

    public synchronized boolean ready(PlayerId player) {
        return ready.contains(player);
    }

    public synchronized Instant deadline() {
        return deadline;
    }

    public Instant now() {
        return clock.instant();
    }

    public synchronized Instant startedAt() {
        return startedAt;
    }

    public synchronized Instant finishedAt() {
        return finishedAt;
    }

    public synchronized GameState state() {
        if (match != null) return match.state();
        Board board = Board.defaultBoard();
        drafts.values().forEach(list -> list.forEach(board::placePiece));
        return setup.withBoard(board).withSequence(revision);
    }

    public synchronized List<Action> legalActions(PlayerId player) {
        if (match == null || match.state().isFinished()) return List.of();
        var actions = new ArrayList<>(generator.getLegalActions(match.state(), player));
        if (uploadedAgents.containsKey(player)) actions.clear();
        for (Action action : List.of(PassAction.of(player), SurrenderAction.of(player)))
            if ((!uploadedAgents.containsKey(player) || action instanceof SurrenderAction) && rules.turnRule().canAct(match.state(), player, action))
                actions.add(action);
        return List.copyOf(actions);
    }

    public synchronized List<Position> placementCells(PlayerId player) {
        if (match != null || ready.contains(player) || !status().equals("PLACEMENT")) return List.of();
        Board empty = Board.defaultBoard();
        var cells = new ArrayList<Position>();
        for (int row = 0; row < 9; row++)
            for (int col = 0; col < 9; col++) {
                Position position = new Position(row, col);
                if (rules.placementRule().canPlace(player, PieceType.ROCK, position, empty)) cells.add(position);
            }
        return List.copyOf(cells);
    }

    public synchronized Map<String, Integer> remaining(PlayerId player) {
        var remaining = new LinkedHashMap<String, Integer>();
        for (var type : PieceType.values())
            remaining.put(type.name(), 6 - (int) drafts.get(player).stream().filter(p -> p.type() == type).count());
        return Map.copyOf(remaining);
    }

    private void editable(PlayerId player) {
        if (!status().equals("PLACEMENT")) fail("INVALID_PHASE");
        if (ready.contains(player)) fail("PLAYER_READY");
    }

    public synchronized void place(PlayerId player, PieceType type, Position target, String pieceId) {
        editable(player);
        var candidate = new ArrayList<>(drafts.get(player));
        Piece existing = pieceId == null ? null : candidate.stream().filter(p -> p.id().equals(pieceId)).findFirst().orElseThrow(() -> new DemoRequestException("PIECE_NOT_FOUND"));
        if (existing != null) {
            type = existing.type();
            candidate.remove(existing);
        }
        if (existing == null && remaining(player).get(type.name()) <= 0) fail("PLACEMENT_QUOTA");
        Board board = Board.defaultBoard();
        candidate.forEach(board::placePiece);
        if (!rules.placementRule().canPlace(player, type, target, board)) fail("INVALID_PLACEMENT");
        String newId = existing != null ? existing.id() : player + "-" + type + "-" + UUID.randomUUID().toString().substring(0, 8);
        candidate.add(Piece.alive(newId, player, type, target));
        drafts.put(player, candidate);
        revision++;
    }

    public synchronized void reset(PlayerId player) {
        editable(player);
        drafts.put(player, new ArrayList<>());
        revision++;
    }

    public synchronized void randomize(PlayerId player) {
        editable(player);
        var cells = new ArrayList<>(placementCells(player));
        Collections.shuffle(cells, new Random(seed ^ player.value().hashCode() ^ revision));
        var list = new ArrayList<Piece>();
        int index = 0;
        for (var type : PieceType.values())
            for (int i = 0; i < 6; i++)
                list.add(Piece.alive(player + "-" + type + "-" + (i + 1), player, type, cells.get(index++)));
        drafts.put(player, list);
        revision++;
    }

    public synchronized List<GameEvent> markReady(PlayerId player) {
        editable(player);
        if (drafts.get(player).size() != 18 || remaining(player).values().stream().anyMatch(count -> count != 0))
            fail("PLACEMENT_INCOMPLETE");
        ready.add(player);
        revision++;
        if (ready.size() != 2) return List.of();
        var engine = new GameEngine(rules);
        var initial = GameState.initial(id, Board.defaultBoard(), setup.players()).withPhase(GamePhase.PLACEMENT).withSequence(revision);
        for (int index = 0; index < 18; index++)
            for (var owner : List.of(P1, P2)) {
                var piece = drafts.get(owner).get(index);
                initial = engine.applyAction(initial, owner, PlaceAction.of(owner, piece.id(), piece.type(), piece.position())).newState();
            }
        PlayerAgent human = (view, actions) -> {
            throw new IllegalStateException("Awaiting submitted action");
        };
        match = new Match(initial.withPhase(GamePhase.READY), rules, uploadedAgents.getOrDefault(P1, human),
                uploadedAgents.getOrDefault(P2, mode == MatchMode.HUMAN_VS_BOT ? new TacticalBotAgent(rules, seed) : human), seed);
        match.start();
        startedAt = clock.instant();
        updateDeadline();
        return List.of(new GameEvent.MatchStarted(match.state().sequence()));
    }

    public synchronized void unready(PlayerId player) {
        if (uploadedBot(player)) fail("BOT_CONTROLLED");
        if (match != null) fail("INVALID_PHASE");
        ready.remove(player);
        revision++;
    }

    public synchronized List<GameEvent> submit(PlayerId player, long sequence, Action action) {
        if (uploadedAgents.containsKey(player) && !(action instanceof SurrenderAction)) fail("BOT_CONTROLLED");
        return applySubmitted(player, sequence, action);
    }

    private List<GameEvent> applySubmitted(PlayerId player, long sequence, Action action) {
        if (match == null) fail("INVALID_PHASE");
        if (match.state().isFinished()) fail("MATCH_FINISHED");
        if (sequence != match.state().sequence()) fail("INVALID_SEQUENCE");
        if (!(action instanceof SurrenderAction) && !match.state().currentPlayerId().equals(player))
            fail("NOT_YOUR_TURN");
        try {
            var transition = match.submitAction(player, action);
            updateDeadline();
            return transition.events();
        } catch (InvalidActionException failure) {
            throw new DemoRequestException(action instanceof MoveAction ? "INVALID_MOVE" : "INVALID_ACTION");
        }
    }

    public synchronized List<GameEvent> tick() {
        if (match == null || match.state().isFinished()) return List.of();
        if (uploadedAgents.containsKey(match.state().currentPlayerId())) return List.of();
        if (mode == MatchMode.HUMAN_VS_BOT && match.state().currentPlayerId().equals(P2)) {
            var transition = match.playTurn();
            updateDeadline();
            return transition.events();
        }
        if (deadline != null && !clock.instant().isBefore(deadline))
            return submit(match.state().currentPlayerId(), match.state().sequence(), PassAction.of(match.state().currentPlayerId()));
        return List.of();
    }

    private void updateDeadline() {
        if (match.state().isFinished()) {
            finishedAt = clock.instant();
            deadline = null;
        } else deadline = clock.instant().plus(turnTime);
        nextBotAt = clock.instant().plusMillis(botDelayMillis);
    }

    public synchronized List<GameEvent> leave(PlayerId player) {
        if (match != null && !match.state().isFinished())
            return submit(player, match.state().sequence(), SurrenderAction.of(player));
        if (match == null) {
            seats.remove(player);
            identities.remove(player);
            connected.remove(player);
            ready.remove(player);
            drafts.put(player, new ArrayList<>());
            uploadedAgents.remove(player);
            botNames.remove(player);
            revision++;
            setup = setup.withPhase(GamePhase.CREATED);
        }
        return List.of();
    }

    public synchronized List<GameEvent> events() {
        return match == null ? List.of() : match.events();
    }
    public synchronized com.rpsbattle.replay.MatchLog replayLog() {
        if (match == null || !match.state().isFinished()) fail("REPLAY_NOT_READY");
        return new com.rpsbattle.replay.MatchLog(id, rules.name(), seed, match.initialState(), match.events(), match.state().result().orElseThrow());
    }

    public record Seat(PlayerId id, String token, boolean bot) {
    }

    public record BotTurn(PlayerId player, long sequence, PlayerAgent agent, GameState state, List<Action> actions) {
    }
}
