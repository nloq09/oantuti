package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
class LargeLocalTournamentAdmissionApiTest extends StageThreeWebFixture {
 @Autowired OwnedTournamentService ownedTournaments;
 @Autowired com.rpsbattle.worker.ExecutionQueue queue;
 @MockitoBean com.rpsbattle.tournament.execution.TournamentExecutionService execution;
 @Test void largeCupStartsAndResumesWithBoundedAdmissionAndRollsBackWhenQueueIsFull() throws Exception {
  loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Resume capacity probe","format","ROUND_ROBIN","participantPolicy","HUMAN_ONLY","maxParticipants",32),201);
  String base="/api/tournaments/"+created.path("id").asText();var t=tournaments.find(created.path("id").asText());
  var seats=new ArrayList<TournamentParticipant>();
  for(int i=0;i<26;i++){String name="resume_"+UUID.randomUUID().toString().substring(0,8);var user=registration.register(name,name+"@example.com","Tournament Test Password",name);seats.add(new TournamentParticipant(UUID.randomUUID().toString(),null,"",name,i,user.id().value()));}
  ownedTournaments.submissionMutation(t,com.rpsbattle.auth.AuthService.sessionAuthentication(account),()->{
   t.participants.addAll(seats);
   t.status=TournamentStatus.READY;tournaments.changed(t);
  });
  assertThat(queue.size()).isZero();
  var occupied=new ArrayList<String>();for(int i=0;i<queue.capacity();i++)occupied.add(UUID.randomUUID().toString());queue.reserve(occupied);
  try{
   assertThat(request(base+"/start","POST",Map.of(),503).path("error").asText()).isEqualTo("SERVER_BUSY");
   assertThat(t.status).isEqualTo(TournamentStatus.READY);assertThat(t.matches).isEmpty();assertThat(queue.reservedIds()).containsExactlyInAnyOrderElementsOf(occupied);
  }finally{queue.release(occupied);}
  request(base+"/start","POST",Map.of(),200);assertThat(t.matches).hasSize(325);
  var schedule=t.matches.stream().map(m->m.id).toList();var initial=queue.reservedIds();assertThat(initial).hasSize(t.maxConcurrentMatches);
  assertThat(t.matches.stream().filter(m->initial.contains(m.id))).allMatch(m->m.round==1);
  request(base+"/control/pause","POST",Map.of("revision",t.revision),200);
  // Start reservations may still exist when the organizer quickly pauses/resumes.
  request(base+"/control/resume","POST",Map.of("revision",t.revision),200);assertThat(queue.reservedIds()).isEqualTo(initial);
  request(base+"/control/pause","POST",Map.of("revision",t.revision),200);queue.release(initial);
  request(base+"/control/resume","POST",Map.of("revision",t.revision),200);
  assertThat(t.status).isEqualTo(TournamentStatus.RUNNING);assertThat(t.matches.stream().map(m->m.id)).containsExactlyElementsOf(schedule);assertThat(queue.reservedIds()).isEqualTo(initial);
  queue.release(initial);
 }
}
