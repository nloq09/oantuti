package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TournamentFlowRegressionTest {
 private static final Instant NOW=Instant.parse("2026-10-21T00:00:00Z");
 @Test void manualReviewAfterDeadlineDoesNotReopenApplications(){
  var f=new TeamTournamentTest();f.owned.clock(Clock.fixed(NOW,ZoneOffset.UTC));
  var manager=f.user("Manager");var auth=com.rpsbattle.auth.AuthService.sessionAuthentication(manager);org.mockito.Mockito.when(f.accounts.requireUser(auth)).thenReturn(manager);
  var t=f.registry.create("Manual deadline",TournamentFormat.ROUND_ROBIN);t.ownerUserId=manager.id().value();t.participantPolicy=ParticipantPolicy.HUMAN_ONLY;t.status=TournamentStatus.REGISTRATION;t.timing=new TournamentTiming(NOW.plusSeconds(10),null,null);
  f.owned.registerHuman(f.aa,t.id,true);f.owned.registerHuman(f.bb,t.id,true);
  f.owned.clock(Clock.fixed(NOW.plusSeconds(10),ZoneOffset.UTC));f.tournaments.clock(Clock.fixed(NOW.plusSeconds(10),ZoneOffset.UTC));
  assertThat(f.tournaments.view(t).get("acceptingRegistrations")).isEqualTo(false);
  assertThatThrownBy(()->f.owned.registerHuman(f.aa,t.id,true)).hasMessage("REGISTRATION_CLOSED");
  for(var r:java.util.List.copyOf(t.registrations))f.owned.review(auth,t.id,r.id().toString(),true);
  assertThat(t.participants).hasSize(2);f.owned.closeRegistration(auth,t.id);assertThat(t.status).isEqualTo(TournamentStatus.READY);
  assertThatThrownBy(()->f.owned.review(auth,t.id,t.registrations.getFirst().id().toString(),false)).hasMessage("TOURNAMENT_LOCKED");
 }
 @Test void humanCapacityDependsOnFormatRatherThanCodingEnrollment(){
  var f=new TeamTournamentTest();for(var format:java.util.List.of(TournamentFormat.SWISS,TournamentFormat.SINGLE_ELIMINATION)){
   var t=f.owned.create(f.aa,"Large humans",format,"PUBLIC",false,4000,null,null,ParticipantPolicy.HUMAN_ONLY,1,null);assertThat(t.maxParticipants).isEqualTo(4000);
  }
  assertThatThrownBy(()->f.owned.create(f.aa,"Too large",TournamentFormat.ROUND_ROBIN,"PUBLIC",false,33,null,null,ParticipantPolicy.HUMAN_ONLY,1,null)).hasMessage("INVALID_PARTICIPANT_LIMIT");
  assertThatThrownBy(()->f.owned.create(f.aa,"Default format",null,"PUBLIC",false,33,null,null,ParticipantPolicy.HUMAN_ONLY,1,null)).hasMessage("INVALID_PARTICIPANT_LIMIT");
 }
 @Test void expiredSchedulesCanBeSavedButCannotBePublishedAndBoundaryIsStrict(){
  var f=new TeamTournamentTest();f.owned.clock(Clock.fixed(NOW,ZoneOffset.UTC));
  var t=f.registry.create("Expired",TournamentFormat.ROUND_ROBIN);t.ownerUserId=f.a.id().value();
  var s=NOW.minusSeconds(1000);var timeline=new TournamentTimeline(s,s.plusSeconds(60),s.plusSeconds(120),s.plusSeconds(180),s.plusSeconds(190),s.plusSeconds(210),s.plusSeconds(240),s.plusSeconds(270),s.plusSeconds(300),s.plusSeconds(360),null);
  f.owned.configureTimeline(f.aa,t.id,t.revision,timeline);long revision=t.revision;
  assertThatThrownBy(()->f.owned.openRegistration(f.aa,t.id)).hasMessage("TOURNAMENT_SCHEDULE_EXPIRED");assertThat(t.status).isEqualTo(TournamentStatus.DRAFT);assertThat(t.revision).isEqualTo(revision);
  f.owned.clock(Clock.fixed(timeline.registrationCloseAt(),ZoneOffset.UTC));assertThatThrownBy(()->f.owned.openRegistration(f.aa,t.id)).hasMessage("TOURNAMENT_SCHEDULE_EXPIRED");
  f.owned.clock(Clock.fixed(timeline.registrationCloseAt().minusNanos(1),ZoneOffset.UTC));f.owned.openRegistration(f.aa,t.id);assertThat(t.status).isEqualTo(TournamentStatus.REGISTRATION);
  assertThatThrownBy(()->f.owned.review(f.aa,t.id,"unused",true)).hasMessage("REGISTRATION_PARTICIPANT_ONLY");
 }
 @Test void manualPublicationRejectsExpiredDeadline(){var f=new TeamTournamentTest();f.owned.clock(Clock.fixed(NOW,ZoneOffset.UTC));var t=f.registry.create("Manual",TournamentFormat.ROUND_ROBIN);t.ownerUserId=f.a.id().value();t.timing=new TournamentTiming(NOW,null,null);assertThatThrownBy(()->f.owned.openRegistration(f.aa,t.id)).hasMessage("TOURNAMENT_SCHEDULE_EXPIRED");assertThat(t.status).isEqualTo(TournamentStatus.DRAFT);}
 @Test void manualDatesMatchWizardIncludingMissingIntermediateDate(){
  assertThatThrownBy(()->new TournamentTiming(NOW,NOW,NOW).validate()).hasMessage("INVALID_TOURNAMENT_TIMING");
  new TournamentTiming(NOW,NOW.plusSeconds(1),NOW.plusSeconds(1)).validate();new TournamentTiming(NOW,null,NOW).validate();
  assertThatThrownBy(()->new TournamentTiming(NOW,null,NOW.minusSeconds(1)).validate()).hasMessage("INVALID_TOURNAMENT_TIMING");
 }
}
