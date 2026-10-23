package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.agent.PlayerView;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.engine.GameEngine;
import com.rpsbattle.game.engine.LegalActionGenerator;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.piece.PieceType;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rules.classic.ClassicRuleSet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Keep copyable public documentation examples tied to the production codec and engine. */
class BotDocumentationTest {
    @Test
    void publishedRequestAndReplyMatchTheRealProtocolAndLegalMoves() throws Exception {
        var json = new ObjectMapper();
        var p1 = PlayerId.of("P1");
        var p2 = PlayerId.of("P2");
        var board = Board.defaultBoard();
        board.placePiece(Piece.alive("P1-ROCK-1", p1, PieceType.ROCK, new Position(6, 0)));
        board.placePiece(Piece.alive("P2-SCISSORS-1", p2, PieceType.SCISSORS, new Position(1, 8)));
        var players = List.of(Player.of(p1, "Player 1").withAddedPiece("P1-ROCK-1"),
                Player.of(p2, "Player 2").withAddedPiece("P2-SCISSORS-1"));
        var state = GameState.initial("DOCS-DEMO", board, players)
                .withPhase(GamePhase.PLAYING).withTurnNumber(24).withSequence(70);
        var rules = new ClassicRuleSet(p1, p2);
        var legal = new LegalActionGenerator(rules).getLegalActions(state, p1);
        var protocol = new BotProtocol();
        var samples = Path.of("../docs/bot/examples");
        String request = Files.readString(samples.resolve("turn.jsonl")).strip();
        String response = Files.readString(samples.resolve("response.jsonl")).strip();
        assertThat(request).doesNotContain("\n", "\r");
        assertThat(response).doesNotContain("\n", "\r");
        assertThat(json.readTree(request)).isEqualTo(json.readTree(protocol.encodeTurn(new PlayerView(state, p1), legal)));
        var action = protocol.decodeResponse(response, state.sequence(), p1);
        assertThat(action).isEqualTo(legal.getFirst());
        var next = new GameEngine(rules).applyAction(state, p1, action).newState();
        assertThat(next.board().getPiece(new Position(5, 0))).isPresent();
        assertThat(next.sequence()).isGreaterThan(state.sequence());

        // The readable Markdown example must stay equal to the runnable JSON Lines files too.
        String markdown = Files.readString(Path.of("../docs/bot/protocol.md"));
        var blocks = Pattern.compile("```json\\R(.*?)\\R```", Pattern.DOTALL).matcher(markdown);
        assertThat(blocks.find()).isTrue();
        assertThat(json.readTree(blocks.group(1))).isEqualTo(json.readTree(request));
        assertThat(blocks.find()).isTrue();
        assertThat(json.readTree(blocks.group(1))).isEqualTo(json.readTree(response));
    }
}
