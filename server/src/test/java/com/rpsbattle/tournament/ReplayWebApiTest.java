package com.rpsbattle.tournament;
import org.junit.jupiter.api.Test;
import com.rpsbattle.online.OnlineMatch;
import com.rpsbattle.game.action.*;
import static org.assertj.core.api.Assertions.*;
class ReplayWebApiTest extends StageThreeWebFixture {
    @Test void finishedReplayContainsInitialArmyAllEventsAndResult()throws Exception {
        var room=playing();String path="/api/matches/"+room.id()+"/replay";
        request(path,"GET",null,400);
        online.publish(room,room.submit(OnlineMatch.P1,room.state().sequence(),PassAction.of(OnlineMatch.P1)));
        online.publish(room,room.submit(OnlineMatch.P2,room.state().sequence(),SurrenderAction.of(OnlineMatch.P2)));
        var replay=request(path,"GET",null,200);assertThat(replay.path("initialState").path("pieces").size()).isEqualTo(36);
        assertThat(replay.path("events").size()).isEqualTo(room.events().size());assertThat(replay.path("result").path("winner").asText()).isEqualTo("P1");
        assertThat(replay.path("finalState").path("phase").asText()).isEqualTo("FINISHED");assertThat(replay.toString()).doesNotContain(room.creator().token());
    }
}
