package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Organizer decisions must win over asynchronous validation and global timeline changes. */
class ValidationDecisionRaceApiTest extends FinalValidationApiTest {
 private void reject(String base)throws Exception {
  var submission=request(base+"/submission/me","GET",null,200);
  managerRequest(base+"/submissions/"+submission.path("id").asText()+"/action","POST",Map.of("revision",tournament(base).revision,"action","reject","reason","Organizer disqualified this submission"),200);
  assertThat(request(base+"/submission/me","GET",null,200).path("status").asText()).isEqualTo("DISQUALIFIED");
 }
 @Test void completedValidationCannotOverwriteLaterOrganizerDisqualification()throws Exception {
  String base=exam("DISQUALIFY");ready(base,130);freeze(base);enqueue(base);
  var lease=queue.lease();assertThat(lease.finalValidationId()).isNotNull();finalWorker.run(lease);
  assertThat(jdbc.queryForObject("select status from tournament_final_validations where id=?",String.class,lease.finalValidationId())).isEqualTo("PASSED");
  reject(base);
  finalService.process(id(base));
  assertThat(request(base+"/submission/me","GET",null,200).path("status").asText()).isEqualTo("DISQUALIFIED");
  assertThat(tournament(base).participants).isEmpty();
  assertThat(request(base+"/participant-flow","GET",null,200).path("finalValidation").path("reason").asText()).isEqualTo("SUBMISSION_REJECTED");
  when(clock.instant()).thenReturn(START.plusSeconds(600));
  request(base+"/check-in","POST",Map.of(),400);
  assertThat(tournament(base).registrations.getFirst().status()).isEqualTo(TournamentRegistration.Status.REJECTED);
 }
 @Test void globalCodingExtensionPreservesManuallyDisqualifiedSubmission()throws Exception {
  String base=exam("DISQUALIFY");ready(base,130);freeze(base);reject(base);
  var t=tournament(base);
  managerRequest(base+"/timeline/override","POST",Map.of("revision",t.revision,"action","EXTEND_CODING","value",START.plusSeconds(300),"reason","Extend coding after outage"),200);
  assertThat(request(base+"/submission/me","GET",null,200).path("status").asText()).isEqualTo("DISQUALIFIED");
  assertThatThrownBy(()->submissions.requireReplaceable(t,account.id().value())).hasMessage("SUBMISSION_DISQUALIFIED");
  var attempt=request(base+"/submission/me/attempts","POST",Map.of("requestId",UUID.randomUUID().toString(),"language","PYTHON","sourceCode","print('new submission')\n","rulesAccepted",true),409);
  assertThat(attempt.path("error").asText()).isEqualTo("SUBMISSION_DISQUALIFIED");
  assertThat(request(base+"/participant-flow","GET",null,200).path("canModifyWorkspace").asBoolean()).isFalse();
  when(clock.instant()).thenReturn(START.plusSeconds(300));deadlines.processDeadlines();
  assertThat(submissions.frozenMine(t.id,account.id().value(),START.plusSeconds(300)).decision()).isEqualTo("MISSING");
 }
 @Test void unlockedRejectedSubmissionStaysRejectedAcrossGlobalExtension()throws Exception {
  String base=exam("DISQUALIFY");var w=ready(base,130);submit(base,w);var t=tournament(base);
  var s=request(base+"/submission/me","GET",null,200);
  managerRequest(base+"/submissions/"+s.path("id").asText()+"/action","POST",Map.of("revision",t.revision,"action","reject","reason","Reject before coding deadline"),200);
  when(clock.instant()).thenReturn(START.plusSeconds(185));
  managerRequest(base+"/timeline/override","POST",Map.of("revision",t.revision,"action","EXTEND_CODING","value",START.plusSeconds(300),"reason","Extend for eligible entrants"),200);
  assertThat(request(base+"/submission/me","GET",null,200).path("status").asText()).isEqualTo("REJECTED");
  assertThat(request(base+"/participant-flow","GET",null,200).path("canModifyWorkspace").asBoolean()).isFalse();
  assertThatThrownBy(()->submissions.requireReplaceable(t,account.id().value())).hasMessage("RESUBMIT_DISABLED");
  when(clock.instant()).thenReturn(START.plusSeconds(300));deadlines.processDeadlines();
  assertThat(submissions.frozenMine(t.id,account.id().value(),START.plusSeconds(300)).decision()).isEqualTo("MISSING");
 }
 @Test void manualRejectionCannotUseEmergencyResubmissionToRegainAdmission()throws Exception {
  String base=exam("ALLOW_EMERGENCY_RESUBMISSION");ready(base,130);freeze(base);hiddenFailure();enqueue(base);
  var validation=finalDone(base).path("finalValidation");assertThat(validation.path("canGrantEmergency").asBoolean()).isTrue();
  var s=request(base+"/submission/me","GET",null,200);var t=tournament(base);
  managerRequest(base+"/submissions/"+s.path("id").asText()+"/action","POST",Map.of("revision",t.revision,"action","reject","reason","Independent organizer rejection"),200);
  managerRequest(base+"/final-validations/"+validation.path("id").asText()+"/emergency","POST",Map.of("revision",t.revision,"reason","Must preserve organizer rejection","deadline",START.plusSeconds(250)),409);
  assertThat(request(base+"/participant-flow","GET",null,200).path("finalValidation").path("canGrantEmergency").asBoolean()).isFalse();
 }

}
