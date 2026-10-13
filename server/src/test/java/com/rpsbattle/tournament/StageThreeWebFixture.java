package com.rpsbattle.tournament;
import com.fasterxml.jackson.databind.*;
import com.rpsbattle.online.*;
import com.rpsbattle.bot.upload.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rps.bots.directory=build/test-stage3-bots")
abstract class StageThreeWebFixture {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired OnlineMatchService online;
    @Autowired BotUploadService uploads;
    @Autowired TournamentService tournaments;
    JsonNode request(String path,String method,Object body,int expected) throws Exception {
        return request(path,method,body,expected,Map.of());
    }
    JsonNode request(String path,String method,Object body,int expected,Map<String,String> headers) throws Exception {
        try(var http=HttpClient.newHttpClient()) {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            headers.forEach(builder::header);
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);return json.readTree(response.body());
        }
    }
    BotUploadService.Upload upload(String name){return uploads.upload(name,new MockMultipartFile("file","main.py","text/plain","print('test')".getBytes(StandardCharsets.UTF_8)));}
    OnlineMatch playing(){var room=online.create(MatchMode.HUMAN_VS_HUMAN,42L);room.join();for(var id:List.of(OnlineMatch.P1,OnlineMatch.P2)){room.randomize(id);room.markReady(id);}return room;}
    class Peer implements WebSocket.Listener,AutoCloseable {
        final LinkedBlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>();
        final StringBuilder fragments=new StringBuilder();final HttpClient http=HttpClient.newHttpClient();final WebSocket socket;
        Peer() {socket=http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/game"),this).join();}
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){fragments.append(text);if(last){try{messages.add(json.readTree(fragments.toString()));}catch(Exception error){throw new RuntimeException(error);}fragments.setLength(0);}socket.request(1);return null;}
        void send(Object message)throws Exception{socket.sendText(json.writeValueAsString(message),true).join();}
        JsonNode await(Predicate<JsonNode> predicate)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<deadline){var m=messages.poll(100,TimeUnit.MILLISECONDS);if(m!=null&&predicate.test(m))return m;}throw new AssertionError("WebSocket response timeout");}
        public void close(){socket.sendClose(1000,"test complete").join();http.close();}
    }
}
