package com.rpsbattle.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoWebSocketIntegrationTest {
    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper json;

    private Map<String, Object> move(String piece, int r1, int c1, int r2, int c2) {
        return Map.of("type", "MOVE", "pieceId", piece, "from", Map.of("row", r1, "col", c1), "to", Map.of("row", r2, "col", c2));
    }

    private void joinBoth(Peer p1, Peer p2) throws Exception {
        p1.join("P1");
        p1.state(0);
        p2.join("P2");
        p1.state(0);
        p2.state(0);
    }

    @Test
    void twoRealClientsMoveCombatReceiveIdenticalBoardAndGameOver() throws Exception {
        try (var p1 = new Peer(); var p2 = new Peer()) {
            joinBoth(p1, p2);
            Object[][] steps = {
                    {"P1", "P1-R1", 6, 2, 5, 2}, {"P2", "P2-S1", 2, 2, 3, 2},
                    {"P1", "P1-R1", 5, 2, 4, 2}, {"P2", "P2-R1", 1, 6, 2, 6},
                    {"P1", "P1-R1", 4, 2, 3, 2}
            };
            JsonNode finalState = null;
            for (int index = 0; index < steps.length; index++) {
                var step = steps[index];
                Peer sender = step[0].equals("P1") ? p1 : p2;
                sender.action((String) step[0], index, move((String) step[1], (int) step[2], (int) step[3], (int) step[4], (int) step[5]));
                var a = p1.state(index + 1);
                var b = p2.state(index + 1);
                assertThat(a.path("pieces")).isEqualTo(b.path("pieces"));
                assertThat(a.path("events")).isEqualTo(b.path("events"));
                finalState = a;
            }
            assertThat(finalState.path("pieces").size()).isEqualTo(3);
            assertThat(finalState.path("events").toString()).contains("COMBAT_OCCURRED", "ATTACKER_WIN", "P2-S1");
            assertThat(finalState.path("pieces").toString()).doesNotContain("P2-S1");
            assertThat(finalState.path("legalActions").isEmpty()).isTrue(); // P1 is waiting.
            p2.action("P2", 5, Map.of("type", "SURRENDER"));
            assertThat(p1.state(6).path("winner").asText()).isEqualTo("P1");
            assertThat(p2.state(6).path("phase").asText()).isEqualTo("FINISHED");
            assertThat(p1.take("GAME_OVER").path("winner").asText()).isEqualTo("P1");
            assertThat(p2.take("GAME_OVER").path("winner").asText()).isEqualTo("P1");
            p1.action("P1", 6, Map.of("type", "PASS"));
            p1.rejected("MATCH_FINISHED");
        }
    }

    @Test
    void rejectsBadMessagesStaleActionsSpoofedIdentityAndDuplicateSeats() throws Exception {
        try (var p1 = new Peer(); var p2 = new Peer(); var stranger = new Peer()) {
            p1.join("P1");
            p1.state(0);
            p1.action("P1", 0, Map.of("type", "PASS"));
            p1.rejected("WAITING_FOR_PLAYER");
            p2.join("P2");
            p1.state(0);
            p2.state(0);
            stranger.action("P1", 0, Map.of("type", "PASS"));
            stranger.rejected("JOIN_REQUIRED");
            stranger.join("P1");
            stranger.rejected("SEAT_TAKEN");
            stranger.join("P3");
            stranger.rejected("PLAYER_NOT_FOUND");
            stranger.send(Map.of("type", "JOIN", "matchId", "missing", "playerId", "P1"));
            stranger.rejected("MATCH_NOT_FOUND");
            stranger.raw("not-json");
            stranger.rejected("INVALID_MESSAGE");
            stranger.raw("null");
            stranger.rejected("INVALID_MESSAGE");
            stranger.raw("{\"type\":\"JOIN\",\"matchId\":\"demo-match\",\"playerId\":\"P1\"} {}");
            stranger.rejected("INVALID_MESSAGE");
            p1.action("P2", 0, Map.of("type", "PASS"));
            p1.rejected("PLAYER_MISMATCH");
            p2.action("P2", 0, Map.of("type", "PASS"));
            p2.rejected("NOT_YOUR_TURN");
            p1.action("P1", 0, move("P1-R1", 6, 2, 4, 3));
            p1.rejected("INVALID_MOVE");
            p1.action("P1", 1, Map.of("type", "PASS"));
            p1.rejected("INVALID_SEQUENCE");
            p1.state(0);
            p1.send(Map.of("type", "ACTION", "matchId", "demo-match", "playerId", "P1", "sequence", 0.5, "payload", Map.of("type", "PASS")));
            p1.rejected("INVALID_MESSAGE");
            p1.action("P1", 0, move("P1-R1", 6, 2, 5, 2));
            p1.state(1);
            p2.state(1);
            p1.action("P1", 0, move("P1-R1", 6, 2, 5, 2));
            p1.rejected("INVALID_SEQUENCE");
            p1.state(1);
            p2.action("P2", 1, Map.of("type", "PASS"));
            p1.state(2);
            p2.state(2);
        }
    }

    @Test
    void servesClientAssetsAndReleasesDisconnectedSeat() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/", "/game.html", "/game.js", "/style.css"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isNotBlank();
            }
        }
        try (var p2 = new Peer()) {
            try (var first = new Peer()) {
                joinBoth(first, p2);
            }
            try (var replacement = new Peer()) {
                replacement.join("P1");
                replacement.state(0);
                p2.state(0);
                replacement.action("P1", 0, Map.of("type", "PASS"));
                replacement.state(1);
                p2.state(1);
            }
        }
    }

    private final class Peer implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        final CompletableFuture<Void> closed = new CompletableFuture<>();
        final WebSocket socket;

        Peer() throws Exception {
            socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws/game"), this).get(5, TimeUnit.SECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                messages.add(fragments.toString());
                fragments.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
            closed.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.completeExceptionally(error);
        }

        void raw(String text) throws Exception {
            socket.sendText(text, true).get(3, TimeUnit.SECONDS);
        }

        void send(Object value) throws Exception {
            raw(json.writeValueAsString(value));
        }

        JsonNode take(String type) throws Exception {
            String text = messages.poll(5, TimeUnit.SECONDS);
            assertThat(text).as("Expected WebSocket message %s", type).isNotNull();
            var message = json.readTree(text);
            assertThat(message.path("type").asText()).isEqualTo(type);
            return message.path("payload");
        }

        JsonNode state(long sequence) throws Exception {
            var state = take("STATE").path("gameState");
            assertThat(state.path("sequence").asLong()).isEqualTo(sequence);
            return state;
        }

        void join(String player) throws Exception {
            send(Map.of("type", "JOIN", "matchId", "demo-match", "playerId", player));
        }

        void action(String player, long sequence, Object payload) throws Exception {
            send(Map.of("type", "ACTION", "matchId", "demo-match", "playerId", player, "sequence", sequence, "payload", payload));
        }

        void rejected(String code) throws Exception {
            assertThat(take("ACTION_REJECTED").path("error").asText()).isEqualTo(code);
        }

        @Override
        public void close() throws Exception {
            if (!socket.isOutputClosed())
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").get(3, TimeUnit.SECONDS);
            closed.get(3, TimeUnit.SECONDS);
        }
    }
}
