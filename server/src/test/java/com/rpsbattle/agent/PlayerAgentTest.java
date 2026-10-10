package com.rpsbattle.agent;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.*;
import com.rpsbattle.game.player.*;
import com.rpsbattle.game.state.GameState;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class PlayerAgentTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    private final PlayerView view = new PlayerView(GameState.initial("M1", Board.defaultBoard(),
            List.of(Player.of(P1, "A"), Player.of(P2, "B"))), P1);
    private final List<Action> actions = List.of(
            MoveAction.of(P1, "a", new Position(4, 4), new Position(3, 4)),
            MoveAction.of(P1, "a", new Position(4, 4), new Position(4, 5)));

    @Test
    void injectedRandomGeneratorAndSeedProduceSameChoices() {
        RandomBotAgent a = new RandomBotAgent(new Random(123)), b = new RandomBotAgent(123);
        for (int i = 0; i < 50; i++) {
            Action choice = a.chooseAction(view, actions);
            assertThat(choice).isIn(actions).isEqualTo(b.chooseAction(view, actions));
        }
    }

    @Test
    void botRejectsEmptyActionListClearly() {
        assertThatThrownBy(() -> new RandomBotAgent(1).chooseAction(view, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No legal actions");
    }

    @Test
    void humanSelectsListedActionAndRetriesMalformedInput() {
        StringWriter output = new StringWriter();
        ConsoleHumanAgent human = new ConsoleHumanAgent(new StringReader("oops\n0\n99\n2\n"), output);
        assertThat(human.chooseAction(view, actions)).isEqualTo(actions.get(1));
        assertThat(output.toString()).contains("Player P1", "Enter a listed action");
    }

    @Test
    void humanCanPassAndSurrenderWithoutImplementingRules() {
        ConsoleHumanAgent human = new ConsoleHumanAgent(new StringReader(" pass \nSURRENDER\n"), new StringWriter());
        assertThat(human.chooseAction(view, actions)).isEqualTo(PassAction.of(P1));
        assertThat(human.chooseAction(view, actions)).isEqualTo(SurrenderAction.of(P1));
    }

    @Test
    void humanEofDoesNotLoopForever() {
        ConsoleHumanAgent human = new ConsoleHumanAgent(new StringReader(""), new StringWriter());
        assertThatThrownBy(() -> human.chooseAction(view, actions)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("input ended");
    }

    @Test
    void twoHumansCanShareOneBufferedConsoleInput() {
        BufferedReader shared = new BufferedReader(new StringReader("1\n2\n"));
        ConsoleHumanAgent a = new ConsoleHumanAgent(shared, new StringWriter());
        ConsoleHumanAgent b = new ConsoleHumanAgent(shared, new StringWriter());
        assertThat(a.chooseAction(view, actions)).isEqualTo(actions.get(0));
        assertThat(b.chooseAction(view, actions)).isEqualTo(actions.get(1));
    }

    @Test
    void viewRejectsUnknownPlayer() {
        assertThatThrownBy(() -> new PlayerView(view.state(), PlayerId.of("outsider")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
