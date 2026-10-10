package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.rules.classic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BotProtocolTest {
    private static final PlayerId P1 = PlayerId.of("P1"), P2 = PlayerId.of("P2");
    private final BotProtocol protocol = new BotProtocol();

    @Test
    void requestContainsStateAndLegalActionsOnOneLine() throws Exception {
        var state = ClassicGameSetup.create("M1", P1, P2).withPhase(com.rpsbattle.game.state.GamePhase.PLAYING);
        var legal = new LegalActionGenerator(new ClassicRuleSet(P1, P2)).getLegalActions(state, P1);
        String line = protocol.encodeTurn(new PlayerView(state, P1), legal);
        assertThat(line).doesNotContain("\n", "\r");
        var json = new ObjectMapper().readTree(line);
        assertThat(json.get("type").asText()).isEqualTo("YOUR_TURN");
        assertThat(json.get("player").asText()).isEqualTo("P1");
        assertThat(json.get("matchId").asText()).isEqualTo("M1");
        assertThat(json.get("sequence").asLong()).isEqualTo(36);
        assertThat(json.get("turn").asInt()).isZero();
        assertThat(json.get("state").get("board").get("pieces").size()).isEqualTo(36);
        assertThat(json.get("state").get("board").get("rows").asInt()).isEqualTo(9);
        assertThat(json.get("legalActions").size()).isEqualTo(legal.size());
        var first = json.get("legalActions").get(0);
        String reply = "{\"type\":\"ACTION\",\"sequence\":36,\"action\":" + first + "}";
        assertThat(protocol.decodeResponse(reply, 36, P1)).isEqualTo(legal.get(0));
    }

    @Test
    void parsesExampleMoveAndAssignsTrustedPlayer() {
        String reply = """
                {"type":"ACTION","sequence":20,"action":{"type":"MOVE","pieceId":"P1-04",
                "from":{"row":5,"col":3},"to":{"row":4,"col":3}}}
                """;
        assertThat(protocol.decodeResponse(reply, 20, P1))
                .isEqualTo(MoveAction.of(P1, "P1-04", new Position(5, 3), new Position(4, 3)));
    }

    @Test
    void supportsPassAndSurrender() {
        for (String type : List.of("PASS", "SURRENDER")) {
            Action action = protocol.decodeResponse("{\"type\":\"ACTION\",\"sequence\":0,\"action\":{\"type\":\"" + type + "\"}}", 0, P1);
            assertThat(action).isEqualTo(type.equals("PASS") ? PassAction.of(P1) : SurrenderAction.of(P1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "null", "[]", "{}",
            "{\"type\":\"OTHER\",\"sequence\":0,\"action\":{\"type\":\"PASS\"}}",
            "{\"type\":\"ACTION\",\"action\":{\"type\":\"PASS\"}}",
            "{\"type\":\"ACTION\",\"sequence\":1,\"action\":{\"type\":\"PASS\"}}",
            "{\"type\":\"ACTION\",\"sequence\":0.5,\"action\":{\"type\":\"PASS\"}}",
            "{\"type\":\"ACTION\",\"sequence\":0,\"action\":{\"type\":\"PASS\",\"playerId\":\"P2\"}}",
            "{\"type\":\"ACTION\",\"sequence\":0,\"action\":{\"type\":\"MOVE\",\"pieceId\":\"a\"}}",
            "{\"type\":\"ACTION\",\"sequence\":0,\"action\":{\"type\":\"UNKNOWN\"}}",
            "{\"type\":\"ACTION\",\"sequence\":0,\"action\":{\"type\":\"PASS\"}} {}"
    })
    void rejectsMalformedOrStaleResponses(String response) {
        assertThatThrownBy(() -> protocol.decodeResponse(response, 0, P1)).isInstanceOf(BotProtocolException.class);
    }
}
