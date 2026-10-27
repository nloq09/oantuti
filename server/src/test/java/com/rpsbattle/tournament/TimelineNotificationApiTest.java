package com.rpsbattle.tournament;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimelineNotificationApiTest extends TimelineCodingApiTest {
 @Autowired TimelineNotificationService notices;
 @Autowired com.rpsbattle.tournament.persistence.TournamentRepository repository;
 @Test void participantWithoutBotReceivesApprovalAndStageNoticesWithDurableReadCursor()throws Exception{
  String base=cup("BOTH",true),id=base.substring(base.lastIndexOf('/')+1);var t=tournaments.find(id);when(clock.instant()).thenReturn(START.plusSeconds(30));notices.process(id);var feed=request(base+"/notifications","GET",null,200);assertThat(feed.toString()).contains("PARTICIPANT_APPLICATION_REVIEWED","TIMELINE_REGISTRATION_OPENED").doesNotContain("reviewReason","sourceCode","token");assertThat(t.registrations).isEmpty();long revision=t.revision;notices.process(id);assertThat(t.revision).isEqualTo(revision);
  var aggregate=request("/api/account/tournament-notifications","GET",null,200);assertThat(aggregate.path("through").has(id)).isTrue();assertThat(aggregate.path("items").toString()).contains("PARTICIPANT_APPLICATION_REVIEWED","TIMELINE_REGISTRATION_OPENED").doesNotContain("reviewReason","sourceCode","token");
  long through=feed.path("through").asLong();request(base+"/notifications/read","POST",Map.of("through",through),200);assertThat(request(base+"/notifications","GET",null,200).path("unread").asLong()).isZero();var stored=repository.all().stream().filter(r->r.id().toString().equals(id)).findFirst().orElseThrow();assertThat(stored.details().notificationReads()).containsKey(account.id().value().toString());assertThat(stored.details().operations().audit().stream().filter(a->a.type().equals("TIMELINE_REGISTRATION_OPENED"))).hasSize(1);
  when(clock.instant()).thenReturn(START.plusSeconds(120));notices.process(id);assertThat(request(base+"/notifications","GET",null,200).path("unread").asLong()).isPositive();
 }
 @Test void foreignPendingParticipantCannotReadApprovalOrCodingNoticesOfAnotherUser()throws Exception{
  String base=cup("BOTH",true),id=base.substring(base.lastIndexOf('/')+1);var original=account;request("/api/auth/logout","POST",Map.of(),200);account=null;loginAccount();request(base+"/notifications","GET",null,403);request(base+"/participant-registration","POST",Map.of("rulesAccepted",true,"intendedLanguage","PYTHON"),200);when(clock.instant()).thenReturn(START.plusSeconds(120));notices.process(id);var feed=request(base+"/notifications","GET",null,200);assertThat(feed.toString()).doesNotContain("TIMELINE_CODING_STARTED","PARTICIPANT_APPLICATION_REVIEWED",original.id().value().toString());
 }
 @Test void clockBoundaryRemindersAreDeduplicatedAndMissedOlderWarningsAreNotReplayed()throws Exception{
  String base=cup("BOTH",true),id=base.substring(base.lastIndexOf('/')+1);when(clock.instant()).thenReturn(START.plusSeconds(120).minusNanos(1));notices.process(id);assertThat(request(base+"/notifications","GET",null,200).toString()).doesNotContain("TIMELINE_CODING_STARTED");when(clock.instant()).thenReturn(START.plusSeconds(120));notices.process(id);assertThat(request(base+"/notifications","GET",null,200).toString()).contains("TIMELINE_CODING_STARTED","TIMELINE_CODING_1_MIN_LEFT").doesNotContain("TIMELINE_CODING_30_MIN_LEFT","TIMELINE_CODING_10_MIN_LEFT","TIMELINE_CODING_5_MIN_LEFT");
  when(clock.instant()).thenReturn(START.plusSeconds(180));notices.process(id);var feed=request(base+"/notifications","GET",null,200);assertThat(feed.toString()).contains("TIMELINE_CODING_ENDED");long revision=tournaments.find(id).revision;notices.process(id);assertThat(tournaments.find(id).revision).isEqualTo(revision);
 }
 @Test void discoveryIncludesPersonRegistrationBeforeBotAndServerPhaseChangesWithoutRevision()throws Exception{
  String base=cup("BOTH",true),id=base.substring(base.lastIndexOf('/')+1);var joined=request("/api/tournaments/discover?category=joined&page=0&size=50","GET",null,200);var row=java.util.stream.StreamSupport.stream(joined.path("items").spliterator(),false).filter(r->r.path("id").asText().equals(id)).findFirst().orElseThrow();assertThat(row.path("registered").asBoolean()).isTrue();assertThat(row.path("applicationStatus").asText()).isEqualTo("APPROVED");assertThat(row.path("phase").asText()).isEqualTo("REGISTRATION_OPEN");when(clock.instant()).thenReturn(START.plusSeconds(120));joined=request("/api/tournaments/discover?category=joined&page=0&size=50","GET",null,200);assertThat(java.util.stream.StreamSupport.stream(joined.path("items").spliterator(),false).filter(r->r.path("id").asText().equals(id)).findFirst().orElseThrow().path("phase").asText()).isEqualTo("CODING_OPEN");
 }
}
