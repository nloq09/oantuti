package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.TournamentFormat;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TournamentWebSocketTest extends StageThreeWebFixture {
    @Test void updateOnlyWatchNeverStreamsWholeHistoryButLegacyWatchStillDoes()throws Exception {
        var t=tournaments.create("Compact",TournamentFormat.ROUND_ROBIN);
        try(var watcher=new Peer()){
            watcher.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",t.id,"updatesOnly",true));
            var first=watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE"));
            assertThat(first.path("payload").path("tournament").size()).isEqualTo(2);
            tournaments.changed(t);var update=watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE")&&m.path("payload").path("tournament").path("revision").asLong()==t.revision);
            assertThat(update.path("payload").path("tournament").has("matches")).isFalse();
            watcher.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",t.id));var legacy=watcher.await(m->m.path("payload").path("tournament").has("matches"));assertThat(legacy.path("payload").path("tournament").has("participants")).isTrue();
        }
    }
    @Test void watchersReceiveRosterUpdatesAndCannotSendPlayerActions()throws Exception {
        var t=tournaments.create("Watch",TournamentFormat.ROUND_ROBIN);var bot=upload("Watched Bot");
        try(var watcher=new Peer()){
            watcher.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",t.id));watcher.await(m->m.path("type").asText().equals("TOURNAMENT_STATE"));
            tournaments.add(t.id,bot.id(),bot.token(),"Watched Bot",1L);
            var updated=watcher.await(m->m.path("payload").path("tournament").path("participants").size()==1);
            assertThat(updated.toString()).doesNotContain(bot.token());
            watcher.send(Map.of("type","ACTION","matchId","fake"));var rejected=watcher.await(m->m.path("type").asText().equals("ACTION_REJECTED"));assertThat(rejected.path("payload").path("error").asText()).isEqualTo("SPECTATOR_READ_ONLY");
        }
    }
}
