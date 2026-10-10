package com.rpsbattle.websocket;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnlineWebSocketIntegrationTest {
 @LocalServerPort int port;
 @Autowired ObjectMapper json;
 JsonNode post(String path,Object body,int status)throws Exception{try(var client=HttpClient.newHttpClient()){var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/matches"+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(status);return json.readTree(response.body());}}
 final class Peer implements WebSocket.Listener,AutoCloseable {
  final BlockingQueue<String> inbox=new LinkedBlockingQueue<>();final StringBuilder buffer=new StringBuilder();final WebSocket ws;final JsonNode seat;
  Peer(JsonNode seat)throws Exception{this.seat=seat;ws=HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/game"),this).get(5,TimeUnit.SECONDS);}
  public void onOpen(WebSocket socket){socket.request(1);}
  public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){buffer.append(data);if(last){inbox.add(buffer.toString());buffer.setLength(0);}socket.request(1);return CompletableFuture.completedFuture(null);}
  void send(String type,Object payload,Long sequence)throws Exception{var message=new HashMap<String,Object>();message.put("type",type);for(String key:List.of("matchId","playerId","token"))message.put(key,seat.path(key).asText());if(payload!=null)message.put("payload",payload);if(sequence!=null)message.put("sequence",sequence);ws.sendText(json.writeValueAsString(message),true).get(3,TimeUnit.SECONDS);}
  JsonNode await(Predicate<JsonNode> condition)throws Exception{long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(System.nanoTime()<limit){var text=inbox.poll(1,TimeUnit.SECONDS);if(text!=null){var value=json.readTree(text);if(condition.test(value))return value.path("payload");}}throw new AssertionError("WebSocket response missing");}
  JsonNode state(String status)throws Exception{return await(m->m.path("type").asText().equals("GAME_STATE")&&m.path("payload").path("snapshot").path("status").asText().equals(status)).path("snapshot");}
  void rejected(String code)throws Exception{assertThat(await(m->m.path("type").asText().equals("ACTION_REJECTED")).path("error").asText()).isEqualTo(code);}
  public void close(){ws.abort();}
 }
 @Test void twoPeoplePlaceReadyMoveReconnectAndSurrender()throws Exception{
  var a=post("",Map.of("mode","HUMAN_VS_HUMAN"),201);var b=post("/"+a.path("matchId").asText()+"/join",Map.of(),200);
  try(var p1=new Peer(a);var p2=new Peer(b)){
   p1.send("JOIN_MATCH",null,null);p1.state("PLACEMENT");p2.send("JOIN_MATCH",null,null);p2.state("PLACEMENT");
   p1.send("READY",null,null);p1.rejected("PLACEMENT_INCOMPLETE");
   for(var peer:List.of(p1,p2)){peer.send("RANDOM_PLACEMENT",null,null);peer.await(m->m.path("type").asText().equals("GAME_STATE")&&m.path("payload").path("snapshot").path("remaining").path("ROCK").asInt()==0);peer.send("READY",null,null);}
   var first=p1.state("PLAYING");var second=p2.state("PLAYING");assertThat(first.path("gameState").path("pieces")).isEqualTo(second.path("gameState").path("pieces"));assertThat(first.path("gameState").path("pieces").size()).isEqualTo(36);
   long seq=first.path("gameState").path("sequence").asLong();p1.send("ACTION",Map.of("type","PASS"),seq-1);p1.rejected("INVALID_SEQUENCE");
   JsonNode move=null;for(var action:first.path("gameState").path("legalActions"))if(action.path("type").asText().equals("MOVE")){move=action;break;}assertThat(move).isNotNull();p1.send("ACTION",move,seq);
   var moved=p1.await(m->m.path("type").asText().equals("GAME_STATE")&&m.path("payload").path("snapshot").path("gameState").path("sequence").asLong()==seq+1).path("snapshot");
   try(var resumed=new Peer(a)){resumed.send("RESUME_MATCH",null,null);var restored=resumed.state("PLAYING");assertThat(restored.path("gameState").path("pieces")).isEqualTo(moved.path("gameState").path("pieces"));assertThat(restored.path("gameState").path("events").toString()).contains("PIECE_MOVED");resumed.send("ACTION",Map.of("type","SURRENDER"),seq+1);assertThat(resumed.state("FINISHED").path("gameState").path("winner").asText()).isEqualTo("P2");assertThat(p2.state("FINISHED").path("gameState").path("winner").asText()).isEqualTo("P2");}
  }
 }
 @Test void botAdvancesAndSeatTokenCannotBeForged()throws Exception{var seat=post("",Map.of("mode","HUMAN_VS_BOT","seed",77),201);var forged=seat.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)forged).put("token","wrong");try(var stranger=new Peer(forged);var player=new Peer(seat)){stranger.send("JOIN_MATCH",null,null);stranger.rejected("PLAYER_NOT_FOUND");player.send("JOIN_MATCH",null,null);player.state("PLACEMENT");player.send("RANDOM_PLACEMENT",null,null);player.await(m->m.path("type").asText().equals("GAME_STATE")&&m.path("payload").path("snapshot").path("remaining").path("ROCK").asInt()==0);player.send("READY",null,null);var playing=player.state("PLAYING");long seq=playing.path("gameState").path("sequence").asLong();player.send("ACTION",Map.of("type","PASS"),seq);var bot=player.await(m->m.path("type").asText().equals("GAME_STATE")&&m.path("payload").path("snapshot").path("gameState").path("sequence").asLong()==seq+2).path("snapshot");assertThat(bot.path("gameState").path("currentPlayer").asText()).isEqualTo("P1");player.send("LEAVE",null,null);assertThat(player.state("FINISHED").path("gameState").path("winner").asText()).isEqualTo("P2");player.await(m->m.path("type").asText().equals("LEFT_ROOM"));}}
 @Test void restReportsMissingFullAndInvalidMode()throws Exception{post("",Map.of("mode","GREEDY_BOT"),400);post("/MISSING/join",Map.of(),404);var seat=post("",Map.of("mode","HUMAN_VS_BOT"),201);post("/"+seat.path("matchId").asText()+"/join",Map.of(),409);try(var client=HttpClient.newHttpClient()){for(String path:List.of("/","/lobby.html","/game.html","/result.html","/js/game/game-controller.js","/assets/pieces/rock-blue.svg","/demo/game.html")){var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isNotBlank();}}}
}
