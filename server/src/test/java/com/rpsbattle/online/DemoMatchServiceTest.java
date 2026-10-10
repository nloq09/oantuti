package com.rpsbattle.online;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.player.PlayerId;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DemoMatchServiceTest {
    private final DemoMatchService service = new DemoMatchService();

    private void move(PlayerId player, String piece, int r1, int c1, int r2, int c2) {
        service.submitAction(player, service.state().sequence(), MoveAction.of(player, piece,
                new Position(r1, c1), new Position(r2, c2)));
    }

    @Test
    void startupCreatesReadyToPlaySparseDemo() {
        var state = service.state();
        assertThat(state.matchId()).isEqualTo("demo-match");
        assertThat(state.isPlaying()).isTrue();
        assertThat(state.sequence()).isZero();
        assertThat(state.board().rows()).isEqualTo(9);
        assertThat(state.board().cols()).isEqualTo(9);
        assertThat(state.aliveCount(DemoMatchService.P1)).isEqualTo(2);
        assertThat(state.aliveCount(DemoMatchService.P2)).isEqualTo(2);
        assertThat(service.legalActions(DemoMatchService.P2)).isEmpty();
        assertThat(service.legalActions(DemoMatchService.P1)).anyMatch(action -> action instanceof MoveAction);
    }

    @Test
    void invalidActionsKeepTheOriginalSnapshot() {
        var initial = service.state();
        var bad = MoveAction.of(DemoMatchService.P1, "P1-R1", new Position(6,2), new Position(4,2));
        assertThatThrownBy(() -> service.submitAction(DemoMatchService.P1, 0, bad)).hasMessage("INVALID_MOVE");
        assertThatThrownBy(() -> service.submitAction(DemoMatchService.P1, 1, bad)).hasMessage("INVALID_SEQUENCE");
        assertThatThrownBy(() -> service.submitAction(DemoMatchService.P2, 0, PassAction.of(DemoMatchService.P2))).hasMessage("NOT_YOUR_TURN");
        assertThat(service.state()).isSameAs(initial);
    }

    @Test
    void combatAndEliminationAreResolvedByTheExistingEngine() {
        var p1 = DemoMatchService.P1;
        var p2 = DemoMatchService.P2;
        move(p1, "P1-R1", 6,2,5,2);
        move(p2, "P2-S1", 2,2,3,2);
        move(p1, "P1-R1", 5,2,4,2);
        move(p2, "P2-R1", 1,6,2,6);
        var combat = service.submitAction(p1, 4, MoveAction.of(p1, "P1-R1", new Position(4,2), new Position(3,2)));
        assertThat(combat.events()).anyMatch(event -> event instanceof GameEvent.CombatOccurred);
        assertThat(service.state().board().findById("P2-S1")).isEmpty();
        // Move PAPER to the remaining ROCK; the other player passes through Match.
        for (int row = 7; row > 2; row--) {
            service.submitAction(p2, service.state().sequence(), PassAction.of(p2));
            move(p1, "P1-P1", row,6,row-1,6);
        }
        assertThat(service.state().isFinished()).isTrue();
        assertThat(service.state().result().orElseThrow().winner()).contains(p1);
        assertThat(service.state().aliveCount(p2)).isZero();
        assertThat(service.legalActions(p1)).isEmpty();
        assertThatThrownBy(() -> service.submitAction(p1, service.state().sequence(), PassAction.of(p1)))
                .hasMessage("MATCH_FINISHED");
    }

    @Test
    void racingActionsForTheSameSequenceOnlyAdvanceOnce() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<String> submit = () -> {
                barrier.await(2, TimeUnit.SECONDS);
                try {
                    service.submitAction(DemoMatchService.P1, 0, PassAction.of(DemoMatchService.P1));
                    return "ACCEPTED";
                } catch (DemoRequestException failure) { return failure.code(); }
            };
            var a = executor.submit(submit);
            var b = executor.submit(submit);
            assertThat(List.of(a.get(3, TimeUnit.SECONDS), b.get(3, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ACCEPTED", "INVALID_SEQUENCE");
            assertThat(service.state().sequence()).isEqualTo(1);
        }
    }
}
