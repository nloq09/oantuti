package com.rpsbattle.tournament;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.TournamentDetails;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
class TournamentModeCompatibilityTest {
 @Test void legacyJsonWithoutNotificationCursorRestoresAndSnapshotsDoNotShareReadState()throws Exception{
  var mapper=new ObjectMapper().findAndRegisterModules();var t=new Tournament("fixture","Fixture",TournamentFormat.ROUND_ROBIN,Instant.now());t.notificationReads.put("user",3L);var snapshot=TournamentDetails.snapshot(t);t.notificationReads.put("user",9L);assertThat(snapshot.notificationReads()).containsEntry("user",3L);var old=mapper.valueToTree(snapshot);((com.fasterxml.jackson.databind.node.ObjectNode)old).remove("notificationReads");var restored=new Tournament(t.id,t.name,t.format,t.createdAt);mapper.treeToValue(old,TournamentDetails.class).restore(restored);assertThat(restored.notificationReads).isEmpty();assertThat(restored.participantPolicy).isEqualTo(ParticipantPolicy.BOT_ONLY);assertThat(restored.stages).hasSize(1);
 }
}
