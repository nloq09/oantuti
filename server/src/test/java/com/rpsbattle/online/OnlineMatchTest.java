package com.rpsbattle.online;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.PieceType;

import java.time.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpsbattle.online.OnlineMatch.*;

class OnlineMatchTest {
    OnlineMatch room(MatchMode mode, Clock clock) {
        return new OnlineMatch("TEST", mode, 42, clock, Duration.ofSeconds(30));
    }

    void ready(OnlineMatch room) {
        if (room.mode() == MatchMode.HUMAN_VS_HUMAN) {
            room.join();
            room.randomize(P2);
            room.markReady(P2);
        }
        room.randomize(P1);
        room.markReady(P1);
    }

    void rejects(String code, Runnable action) {
        assertEquals(code, assertThrows(DemoRequestException.class, action::run).code());
    }

    @Test
    void waitingSeatsTokensAndLeavingAreIsolated() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        assertEquals("WAITING", room.status());
        var creator = room.creator();
        room.authorize(P1, creator.token());
        rejects("PLAYER_NOT_FOUND", () -> room.authorize(P1, "wrong"));
        room.join();
        rejects("MATCH_FULL", room::join);
        room.leave(P2);
        assertEquals("WAITING", room.status());
        assertEquals(P2, room.join().id());
    }

    @Test
    void placementEnforcesZoneQuotaAndSupportsRepositionReset() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        room.join();
        rejects("INVALID_PLACEMENT", () -> room.place(P1, PieceType.ROCK, new Position(0, 0), null));
        for (int i = 0; i < 6; i++) room.place(P1, PieceType.ROCK, new Position(6, i), null);
        rejects("PLACEMENT_QUOTA", () -> room.place(P1, PieceType.ROCK, new Position(7, 0), null));
        var piece = room.state().board().getPiece(new Position(6, 0)).orElseThrow();
        room.place(P1, PieceType.PAPER, new Position(7, 0), piece.id());
        assertEquals(PieceType.ROCK, room.state().board().getPiece(new Position(7, 0)).orElseThrow().type());
        assertTrue(room.state().board().isEmpty(new Position(6, 0)));
        rejects("PLACEMENT_INCOMPLETE", () -> room.markReady(P1));
        room.reset(P1);
        assertEquals(6, room.remaining(P1).get("ROCK"));
    }

    @Test
    void strongholdsAreExcludedFromManualRepositionAndRandomDeployment() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        room.join();
        assertFalse(room.placementCells(P1).contains(new Position(8, 0)));
        assertFalse(room.placementCells(P2).contains(new Position(0, 8)));
        rejects("INVALID_PLACEMENT", () -> room.place(P1, PieceType.ROCK, new Position(8, 0), null));
        rejects("INVALID_PLACEMENT", () -> room.place(P2, PieceType.ROCK, new Position(0, 8), null));
        room.place(P1, PieceType.ROCK, new Position(6, 0), null);
        var piece = room.state().board().getPiece(new Position(6, 0)).orElseThrow();
        rejects("INVALID_PLACEMENT", () -> room.place(P1, PieceType.ROCK, new Position(8, 0), piece.id()));
        assertTrue(room.state().board().getPiece(new Position(6, 0)).isPresent());
        for (int i = 0; i < 20; i++) {
            room.randomize(P1);
            room.randomize(P2);
            assertTrue(room.state().board().isEmpty(new Position(8, 0)));
            assertTrue(room.state().board().isEmpty(new Position(0, 8)));
            for (var deployed : room.state().board().allPieces()) {
                var pos = deployed.position();
                assertTrue(deployed.ownedBy(P1) ? pos.row() > pos.col() : pos.row() < pos.col());
            }
        }
        room.markReady(P1);
        room.markReady(P2);
        assertEquals("PLAYING", room.status());
        assertEquals(36, room.state().board().allPieces().size());
        assertTrue(room.state().board().isEmpty(new Position(8, 0)));
        assertTrue(room.state().board().isEmpty(new Position(0, 8)));
    }

    @Test
    void bothReadyStartsValidatedThirtySixPieceBoard() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        room.join();
        room.randomize(P1);
        room.markReady(P1);
        assertEquals("PLACEMENT", room.status());
        rejects("PLAYER_READY", () -> room.reset(P1));
        room.unready(P1);
        room.randomize(P1);
        room.markReady(P1);
        room.randomize(P2);
        room.markReady(P2);
        assertEquals("PLAYING", room.status());
        assertEquals(36, room.state().board().allPieces().size());
        assertEquals(P1, room.state().currentPlayerId());
        assertNotNull(room.deadline());
    }

    @Test
    void staleAndOffTurnMovesCannotMutateButOffTurnSurrenderFinishes() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        ready(room);
        long seq = room.state().sequence();
        rejects("INVALID_SEQUENCE", () -> room.submit(P1, seq - 1, PassAction.of(P1)));
        rejects("NOT_YOUR_TURN", () -> room.submit(P2, seq, PassAction.of(P2)));
        assertEquals(seq, room.state().sequence());
        room.submit(P2, seq, SurrenderAction.of(P2));
        assertEquals("FINISHED", room.status());
        assertEquals(P1, room.state().result().orElseThrow().winner().orElseThrow());
        assertNull(room.deadline());
    }

    @Test
    void deadlinePassesOnceAndBotUsesEngine() {
        var clock = new MutableClock();
        var room = room(MatchMode.HUMAN_VS_BOT, clock);
        ready(room);
        long seq = room.state().sequence();
        assertTrue(room.tick().isEmpty());
        clock.time = clock.time.plusSeconds(30);
        assertFalse(room.tick().isEmpty());
        assertEquals(seq + 1, room.state().sequence());
        assertEquals(P2, room.state().currentPlayerId());
        assertFalse(room.tick().isEmpty());
        assertEquals(seq + 2, room.state().sequence());
        assertEquals(P1, room.state().currentPlayerId());
        assertTrue(room.tick().isEmpty());
    }

    @Test
    void leaveDuringOpponentsTurnIsSurrender() {
        var room = room(MatchMode.HUMAN_VS_HUMAN, Clock.systemUTC());
        ready(room);
        room.leave(P2);
        assertEquals(P1, room.state().result().orElseThrow().winner().orElseThrow());
    }

    @Test
    void tacticalBotCompletesFullGameAgainstLegalRandomMoves() {
        var room = room(MatchMode.HUMAN_VS_BOT, Clock.systemUTC());
        ready(room);
        var random = new java.util.Random(123);
        for (int turn = 0; turn < 200 && !room.state().isFinished(); turn++) {
            if (room.state().currentPlayerId().equals(P2)) room.tick();
            else {
                var moves = room.legalActions(P1).stream().filter(action -> action instanceof MoveAction).toList();
                var action = moves.isEmpty() ? PassAction.of(P1) : moves.get(random.nextInt(moves.size()));
                room.submit(P1, room.state().sequence(), action);
            }
        }
        assertEquals("FINISHED", room.status());
        assertTrue(room.events().stream().anyMatch(event -> event.type().equals("COMBAT_OCCURRED")));
    }

    @Test
    void rejectedMoveKeepsSequenceBoardAndEventLog() {
        var room = room(MatchMode.HUMAN_VS_BOT, Clock.systemUTC());
        ready(room);
        long seq = room.state().sequence();
        int events = room.events().size();
        var piece = room.state().board().allPieces().stream().filter(p -> p.ownedBy(P1)).findFirst().orElseThrow();
        rejects("INVALID_MOVE", () -> room.submit(P1, seq, MoveAction.of(P1, piece.id(), piece.position(), new Position(0, 0))));
        assertEquals(seq, room.state().sequence());
        assertEquals(events, room.events().size());
        assertEquals(piece.position(), room.state().board().findById(piece.id()).orElseThrow().position());
    }

    static class MutableClock extends Clock {
        Instant time = Instant.parse("2026-10-10T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return time;
        }
    }
}
