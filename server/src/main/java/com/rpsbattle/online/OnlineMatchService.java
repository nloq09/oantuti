package com.rpsbattle.online;

import com.rpsbattle.api.dto.*;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.player.PlayerId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.action.Action;

import java.util.concurrent.*;

import jakarta.annotation.PreDestroy;

@Service
public class OnlineMatchService {
    private final MatchRegistry registry;
    private final GameStateMapper mapper;
    private final Clock clock;
    private final Duration turnTime;
    private final List<Consumer<Update>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService bots = Executors.newFixedThreadPool(4);
    private final Semaphore permits = new Semaphore(4);
    private final Map<String, Future<?>> jobs = new ConcurrentHashMap<>();
    private final Set<String> cleaned = ConcurrentHashMap.newKeySet();
    private volatile boolean draining;
    private com.rpsbattle.operations.RuntimeMetrics metrics;
    @org.springframework.beans.factory.annotation.Autowired
    public void metrics(com.rpsbattle.operations.RuntimeMetrics metrics){this.metrics=metrics;}
    public OnlineMatchService(MatchRegistry registry, GameStateMapper mapper, Clock clock,
                              @Value("${rps.turn-seconds:30}") long seconds) {
        if (seconds < 1) throw new IllegalArgumentException("turn-seconds must be positive");
        this.registry = registry;
        this.mapper = mapper;
        this.clock = clock;
        this.turnTime = Duration.ofSeconds(seconds);
    }

    public OnlineMatch create(MatchMode mode, Long seed) {
        if(draining)throw new DemoRequestException("SERVER_BUSY");
        var match = new OnlineMatch(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT),
                mode, seed == null ? UUID.randomUUID().getMostSignificantBits() : seed, clock, turnTime);
        registry.register(match);
        publish(match,List.of());
        return match;
    }

    public OnlineMatch find(String id) {
        return registry.findById(id);
    }

    public OnlineMatch.Seat join(String id) {
        if(draining)throw new DemoRequestException("SERVER_BUSY");
        var room = find(id);
        synchronized (room) {
            var seat = room.join();
            publish(room, room.prepareBots());
            return seat;
        }
    }

    public void listen(Consumer<Update> listener) {
        listeners.add(listener);
    }

    public void publish(OnlineMatch match, List<GameEvent> events) {
        var update = new Update(match, List.copyOf(events));
        for (var listener : listeners) {
            try { listener.accept(update); } catch (RuntimeException ignored) { }
        }
    }

    public OnlineSnapshot snapshot(OnlineMatch room, PlayerId player, List<GameEvent> events) {
        synchronized (room) {
            var seats = List.of(OnlineMatch.P1, OnlineMatch.P2).stream().map(id -> {
                var seat = room.seats().stream().filter(value -> value.id().equals(id)).findFirst();
                return new OnlineSnapshot.PlayerSeatDto(id.value(), seat.isPresent(), room.connected(id), room.ready(id), seat.map(OnlineMatch.Seat::bot).orElse(false), room.botName(id));
            }).toList();
            return new OnlineSnapshot(mapper.toDto(room.state(), room.legalActions(player), events), room.mode().name(),
                    room.status(), player.value(), seats, room.remaining(player), room.placementCells(player).stream().map(PositionDto::from).toList(),
                    room.deadline(), room.now(), room.startedAt(), room.finishedAt(), room.botError());
        }
    }

    @Scheduled(fixedDelayString = "${rps.bot-tick-millis:200}")
    public synchronized void tick() {
        if(draining)return;
        for (var room : registry.getActiveMatches()) {
            if(room.infrastructureError()!=null)continue;
            try {
            synchronized (room) {
                var events = room.tick();
                if (!events.isEmpty()) publish(room, events);
                if (room.state().isFinished()) {
                    var running = jobs.remove(room.id());
                    if (running != null) running.cancel(true);
                    if (cleaned.add(room.id())) closeAgents(room);
                    continue;
                }
                var job = jobs.get(room.id());
                if (job != null && job.isDone()) {
                    jobs.remove(room.id(), job);
                    job = null;
                }
                var pending = room.pendingBotTurn();
                if (job == null && pending.isPresent() && permits.tryAcquire()) {
                    var turn = pending.get();
                    var task = new FutureTask<Void>(() -> {
                        Action action = null;
                        boolean failed = false;long decisionStarted=System.nanoTime();
                        try {
                            action = turn.agent().chooseAction(new PlayerView(turn.state(), turn.player()), turn.actions());
                        } catch (RuntimeException error) {
                            if(metrics!=null)metrics.botFailure(error);
                            failed = true;
                        }
                        long decisionNanos=System.nanoTime()-decisionStarted;
                        synchronized (room) {
                            if(draining)return null;
                            try {
                                var result = room.completeBot(turn, action, failed);
                                if (!result.isEmpty()) {room.recordBotTime(turn.player(),decisionNanos);publish(room, result);}
                            } catch(RuntimeException error) { room.infrastructureFailure(); }
                        }
                        return null;
                    }) {
                        @Override
                        public void run() {
                            try {
                                super.run();
                            } finally {
                                permits.release();
                            }
                        }
                    };
                    jobs.put(room.id(), task);
                    try {
                        bots.execute(task);
                    } catch (RejectedExecutionException error) {
                        jobs.remove(room.id(), task);
                        permits.release();
                    }
                }
            }
            } catch(RuntimeException error) { room.infrastructureFailure(); }
        }
    }

    private void closeAgents(OnlineMatch room) {
        for (var agent : room.uploadedAgents())
            if (agent instanceof AutoCloseable closeable)
                CompletableFuture.runAsync(() -> {
                    try {
                        closeable.close();
                    } catch (Exception ignored) {
                    }
                });
    }
    /** Tournament ownership ends here; replay and spectator archives must be captured first. */
    public void release(String id) {
        var room = registry.findById(id);
        // Capture metadata before tournament cleanup removes the in-memory room.
        publish(room,List.of());
        var running = jobs.remove(id);
        if (running != null) running.cancel(true);
        for (var agent : room.uploadedAgents()) if (agent instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { }
        }
        registry.remove(id);
        cleaned.remove(id);
    }

    @PreDestroy
    public void shutdown() {
        draining=true;
        for (var room : registry.getActiveMatches()) for(var agent:room.uploadedAgents())
            if(agent instanceof AutoCloseable closeable) try {closeable.close();}catch(Exception ignored){}
        bots.shutdownNow();
        try {bots.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
    }
    public synchronized void stopAccepting(){draining=true;}

    public record Update(OnlineMatch match, List<GameEvent> events) {
    }
}
