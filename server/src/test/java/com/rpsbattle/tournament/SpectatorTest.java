package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import java.util.Map;
import com.rpsbattle.online.OnlineMatch;
import com.rpsbattle.game.action.PassAction;
import static org.assertj.core.api.Assertions.*;
class SpectatorTest extends StageThreeWebFixture {
    @Test void readOnlyObserverGetsUpdatesWithoutTakingASeatOrChangingSequence() throws Exception {
        var room=playing();long sequence=room.state().sequence();
        try(var peer=new Peer()){
            peer.send(Map.of("type","SPECTATE_MATCH","matchId",room.id()));var first=peer.await(m->m.path("type").asText().equals("GAME_STATE"));assertThat(first.path("payload").path("snapshot").path("gameState").path("pieces").size()).isEqualTo(36);
            for(var type:java.util.List.of("PLACE","READY","UNREADY","ACTION","LEAVE","JOIN_MATCH")){
                peer.send(Map.of("type",type,"matchId",room.id(),"playerId","P1","token",room.creator().token()));
                var rejection=peer.await(m->m.path("type").asText().equals("ACTION_REJECTED"));assertThat(rejection.path("payload").path("error").asText()).isEqualTo("SPECTATOR_READ_ONLY");
            }
            assertThat(room.state().sequence()).isEqualTo(sequence);assertThat(room.connected(OnlineMatch.P1)).isFalse();
            online.publish(room,room.submit(OnlineMatch.P1,sequence,PassAction.of(OnlineMatch.P1)));
            var updated=peer.await(m->m.path("payload").path("snapshot").path("gameState").path("sequence").asLong()>sequence);assertThat(updated.path("type").asText()).isEqualTo("GAME_STATE");
        }
    }
}
