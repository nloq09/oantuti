package com.rpsbattle.online;

import com.rpsbattle.game.action.*;
import com.rpsbattle.api.mapper.GameStateMapper;

import java.time.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpsbattle.online.OnlineMatch.*;

class UploadedBotMatchTest {
    @Test void infrastructureFailureDoesNotDeclareTheOpponentWinner()throws Exception {
        var clock=new OnlineMatchTest.MutableClock();var service=new OnlineMatchService(new MatchRegistry(),new GameStateMapper(),clock,30);
        try {var room=service.create(MatchMode.HUMAN_VS_BOT,42L);room.attachBot(P1,(view,actions)->{throw new com.rpsbattle.bot.BotInfrastructureException();},"Unavailable");room.prepareBots();clock.time=clock.time.plusSeconds(1);service.tick();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(room.infrastructureError()==null&&System.nanoTime()<deadline)Thread.sleep(10);
            assertNotNull(room.infrastructureError());assertFalse(room.state().isFinished());assertNull(room.botError());assertTrue(room.pendingBotTurn().isEmpty());
        }finally{service.shutdown();}
    }
    OnlineMatch room(OnlineMatchTest.MutableClock clock) {
        return new OnlineMatch("BOTS", MatchMode.HUMAN_VS_HUMAN, 42, clock, Duration.ofSeconds(30));
    }

    void start(OnlineMatch room) {
        room.join();
        room.attachBot(P1, (view, actions) -> actions.getFirst(), "One");
        room.attachBot(P2, (view, actions) -> actions.getFirst(), "Two");
        room.prepareBots();
    }

    @Test
    void botsAutoPlaceAndOwnersCanWatchButCannotTakeOver() {
        var clock = new OnlineMatchTest.MutableClock();
        var room = room(clock);
        String token = room.creator().token();
        start(room);
        assertEquals("PLAYING", room.status());
        assertEquals(36, room.state().board().allPieces().size());
        room.authorize(P1, token);
        assertTrue(room.creator().bot());
        assertEquals("One", room.botName(P1));
        assertTrue(room.legalActions(P1).stream().allMatch(a -> a instanceof SurrenderAction));
        assertEquals("BOT_CONTROLLED", assertThrows(DemoRequestException.class, () -> room.submit(P1, room.state().sequence(), PassAction.of(P1))).code());
        assertEquals("BOT_CONTROLLED", assertThrows(DemoRequestException.class, () -> room.unready(P1)).code());
        clock.time = clock.time.plusSeconds(1);
        var turn = room.pendingBotTurn().orElseThrow();
        assertFalse(room.completeBot(turn, turn.actions().getFirst(), false).isEmpty());
        assertEquals(P2, room.state().currentPlayerId());
        assertNull(room.botError());
    }

    @Test
    void failureAndActionOutsideLegalListForfeit() {
        for (boolean crash : new boolean[]{true, false}) {
            var clock = new OnlineMatchTest.MutableClock();
            var room = room(clock);
            start(room);
            clock.time = clock.time.plusSeconds(1);
            room.completeBot(room.pendingBotTurn().orElseThrow(), PassAction.of(P1), crash);
            assertEquals("FINISHED", room.status());
            assertEquals("BOT_FAILED", room.botError());
            assertEquals(P2, room.state().result().orElseThrow().winner().orElseThrow());
        }
    }

    @Test
    void lateBotResponseCannotOverrideOwnerSurrender() {
        var clock = new OnlineMatchTest.MutableClock();
        var room = room(clock);
        start(room);
        clock.time = clock.time.plusSeconds(1);
        var turn = room.pendingBotTurn().orElseThrow();
        room.submit(P1, turn.sequence(), SurrenderAction.of(P1));
        long sequence = room.state().sequence();
        assertTrue(room.completeBot(turn, turn.actions().getFirst(), false).isEmpty());
        assertEquals(sequence, room.state().sequence());
        assertNull(room.botError());
    }

    @Test
    void waitingBotPreparesWhenHumanJoins() {
        var clock = new OnlineMatchTest.MutableClock();
        var room = room(clock);
        room.attachBot(P1, (view, actions) -> actions.getFirst(), "One");
        assertTrue(room.prepareBots().isEmpty());
        room.join();
        room.prepareBots();
        assertTrue(room.ready(P1));
        assertFalse(room.ready(P2));
        room.randomize(P2);
        room.markReady(P2);
        assertEquals("PLAYING", room.status());
    }

    @Test
    void blockedBotDoesNotBlockOtherRoomsAndCancelledResultIsDiscarded() throws Exception {
        var clock = new OnlineMatchTest.MutableClock();
        var registry = new MatchRegistry();
        var service = new OnlineMatchService(registry, new GameStateMapper(), clock, 30);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var slow = service.create(MatchMode.HUMAN_VS_BOT, 42L);
            slow.attachBot(P1, (view, actions) -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return actions.getFirst();
            }, "Slow");
            slow.prepareBots();
            var other = service.create(MatchMode.HUMAN_VS_BOT, 43L);
            other.randomize(P1);
            other.markReady(P1);
            clock.time = clock.time.plusSeconds(31);
            service.tick();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(P2, other.state().currentPlayerId());
            service.tick();
            assertEquals(P1, other.state().currentPlayerId());
            slow.submit(P1, slow.state().sequence(), SurrenderAction.of(P1));
            long sequence = slow.state().sequence();
            service.tick();
            release.countDown();
            assertEquals("FINISHED", slow.status());
            assertEquals(sequence, slow.state().sequence());
        } finally {
            release.countDown();
            service.shutdown();
        }
    }
}
