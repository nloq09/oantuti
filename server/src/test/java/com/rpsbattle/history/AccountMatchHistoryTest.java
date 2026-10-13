package com.rpsbattle.history;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.online.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rps.history.flush-millis=60000")
class AccountMatchHistoryTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired OnlineMatchService online;
    @Autowired MatchHistoryRepository matches;
    class Browser implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        JsonNode request(String path,String method,Object body,int expected) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(!method.equals("GET")) {var csrf=request("/api/auth/csrf","GET",null,200);builder.header(csrf.path("headerName").asText(),csrf.path("token").asText());}
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);return json.readTree(response.body());
        }
        String login() throws Exception {
            String username="play_"+UUID.randomUUID().toString().substring(0,8);
            var registered=request("/api/auth/register","POST",Map.of("username",username,"email",username+"@example.com","displayName","Player","password","History Test Password"),201);
            request("/api/auth/login","POST",Map.of("username",username,"password","History Test Password"),200);
            return registered.path("id").asText();
        }
        public void close() {http.close();}
    }
    class Peer implements WebSocket.Listener,AutoCloseable {
        final HttpClient http=HttpClient.newHttpClient();final WebSocket ws;
        final JsonNode seat;final LinkedBlockingQueue<JsonNode> inbox=new LinkedBlockingQueue<>();final StringBuilder text=new StringBuilder();
        Peer(JsonNode seat) {this.seat=seat;ws=http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/game"),this).join();}
        public void onOpen(WebSocket socket) {socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence part,boolean last) {text.append(part);if(last) {try {inbox.add(json.readTree(text.toString()));}catch(Exception failure) {throw new RuntimeException(failure);}text.setLength(0);}socket.request(1);return null;}
        void send(String type) throws Exception {ws.sendText(json.writeValueAsString(Map.of("type",type,"matchId",seat.path("matchId").asText(),"playerId",seat.path("playerId").asText(),"token",seat.path("token").asText())),true).join();}
        JsonNode await(Predicate<JsonNode> predicate) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end) {var message=inbox.poll(100,TimeUnit.MILLISECONDS);if(message!=null && predicate.test(message)) return message;}throw new AssertionError("WebSocket timeout");}
        void state(String status) throws Exception {await(m->m.path("type").asText().equals("GAME_STATE") && m.path("payload").path("snapshot").path("status").asText().equals(status));}
        public void close() {ws.sendClose(1000,"test complete").join();http.close();}
    }
    @Test void twoLoggedInPlayersFinishViaWebSocketAndOnlyTheirAccountsReceiveHistory() throws Exception {
        try(var a=new Browser();var b=new Browser();var stranger=new Browser()) {
            var alice=a.login();var bob=b.login();stranger.login();
            var first=a.request("/api/matches","POST",Map.of("mode","HUMAN_VS_HUMAN","seed",42),201);
            String roomId=first.path("matchId").asText(),historyId=first.path("historyId").asText();
            var second=b.request("/api/matches/"+roomId+"/join","POST",Map.of("userId",alice),200);
            assertThat(second.path("historyId").asText()).isEqualTo(historyId);
            try(var p1=new Peer(first);var p2=new Peer(second)) {
                p1.send("JOIN_MATCH");p1.state("PLACEMENT");p2.send("JOIN_MATCH");p2.state("PLACEMENT");
                for(var peer:List.of(p1,p2)) {peer.send("RANDOM_PLACEMENT");peer.await(m->m.path("payload").path("snapshot").path("remaining").path("ROCK").asInt(-1)==0);peer.send("READY");}
                p1.state("PLAYING");p1.send("LEAVE");p2.state("FINISHED");
            }
            var loss=a.request("/api/account/matches?filter=loss&userId="+bob,"GET",null,200);
            assertThat(loss.path("total").asLong()).isEqualTo(1);var archived=loss.path("matches").get(0);
            assertThat(archived.path("id").asText()).isEqualTo(historyId);assertThat(archived.path("roomId").asText()).isEqualTo(roomId);assertThat(archived.path("winnerSeat").asText()).isEqualTo("P2");
            assertThat(archived.path("participants").get(0).path("userId").asText()).isEqualTo(alice);assertThat(archived.path("participants").get(1).path("userId").asText()).isEqualTo(bob);
            assertThat(loss.toString()).doesNotContain("token","password","sourcePath","legalActions","board");
            assertThat(a.request("/api/account/stats","GET",null,200).path("losses").asLong()).isEqualTo(1);
            assertThat(b.request("/api/account/stats","GET",null,200).path("wins").asLong()).isEqualTo(1);
            assertThat(stranger.request("/api/account/matches","GET",null,200).path("total").asLong()).isZero();stranger.request("/api/account/matches/"+historyId,"GET",null,404);
            a.request("/api/account/matches/"+historyId,"GET",null,200);
            online.release(roomId);assertThat(matches.find(historyId)).isPresent();
        }
    }
    @Test void guestWinningStillCountsAsAccountLossAndBuiltinControllerHasNoAccount() throws Exception {
        try(var account=new Browser();var guest=new Browser()) {
            var owner=account.login();var seat=account.request("/api/matches","POST",Map.of("mode","HUMAN_VS_HUMAN"),201);var id=seat.path("matchId").asText();
            guest.request("/api/matches/"+id+"/join","POST",Map.of(),200);var room=online.find(id);
            synchronized(room) {for(var player:List.of(OnlineMatch.P1,OnlineMatch.P2)) {room.randomize(player);room.markReady(player);}online.publish(room,room.leave(OnlineMatch.P1));}
            var result=account.request("/api/account/matches?filter=loss","GET",null,200);
            assertThat(result.path("total").asLong()).isEqualTo(1);assertThat(result.path("matches").get(0).path("outcome").asText()).isEqualTo("LOSS");assertThat(result.path("matches").get(0).path("participants").get(1).path("userId").isNull()).isTrue();
            var stats=account.request("/api/account/stats","GET",null,200);assertThat(stats.path("losses").asLong()).isEqualTo(1);assertThat(stats.path("wins").asLong()).isZero();
            var builtin=account.request("/api/matches","POST",Map.of("mode","HUMAN_VS_BOT"),201);
            var archived=account.request("/api/account/matches/"+builtin.path("historyId").asText(),"GET",null,200);
            assertThat(archived.path("participants").get(0).path("userId").asText()).isEqualTo(owner);assertThat(archived.path("participants").get(1).path("controllerType").asText()).isEqualTo("BUILTIN_BOT");
            assertThat(archived.path("outcome").isNull()).isTrue();assertThat(account.request("/api/account/stats","GET",null,200).path("matchesPlayed").asLong()).isEqualTo(1);
        }
    }
    @Test void historyQueriesRequireLoginAndValidateFilterAndPagination() throws Exception {
        try(var account=new Browser()) {
            account.request("/api/account/matches","GET",null,401);account.request("/api/account/stats","GET",null,401);account.login();
            for(var query:List.of("filter=unknown","page=-1","page=100001","size=0","size=101","size=oops")) account.request("/api/account/matches?"+query,"GET",null,400);
            account.request("/api/account/matches/missing","GET",null,404);
            assertThat(account.request("/api/account/matches?page=10&size=1","GET",null,200).path("matches").size()).isZero();
        }
    }
}
