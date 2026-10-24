package com.rpsbattle.tournament;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest class TournamentNotificationTest {
 @Autowired OwnedTournamentService owned;@Autowired TournamentNotificationService notifications;@Autowired UserRepository users;@Autowired TournamentRepository repository;
 User user(){var now=Instant.now();String name="notice_"+UUID.randomUUID().toString().substring(0,8);return users.save(new User(UserId.random(),name,name+"@example.invalid","fixture",name,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));}
 @Test void noticesArePersonalReadsPersistAndRepeatedReadDoesNotChangeTournament(){
  var owner=AuthService.sessionAuthentication(user());var a=user();var player=AuthService.sessionAuthentication(a);var b=user();var other=AuthService.sessionAuthentication(b);var outsider=AuthService.sessionAuthentication(user());
  var t=owned.create(owner,"Personal notices",TournamentFormat.ROUND_ROBIN,"PUBLIC",false,4,null,null,ParticipantPolicy.HUMAN_ONLY);owned.openRegistration(owner,t.id);owned.registerHuman(player,t.id,true);owned.registerHuman(other,t.id,true);
  var mine=t.registrations.getFirst();owned.review(owner,t.id,mine.id().toString(),true);var foreign=t.registrations.getLast();owned.review(owner,t.id,foreign.id().toString(),false);
  var feed=notifications.list(player,t.id);assertThat(feed.get("items").toString()).contains("PARTICIPANT_APPROVED").doesNotContain(foreign.id().toString());assertThatThrownBy(()->notifications.list(outsider,t.id)).hasMessage("TOURNAMENT_FORBIDDEN");
  long through=((Number)feed.get("through")).longValue();long visible=((java.util.List<java.util.Map<String,Object>>)feed.get("items")).stream().mapToLong(row->((Number)row.get("sequence")).longValue()).max().orElse(0);notifications.read(player,t.id,through);assertThat(notifications.list(player,t.id).get("unread")).isEqualTo(0L);long revision=t.revision;notifications.read(player,t.id,through);assertThat(t.revision).isEqualTo(revision);notifications.read(player,t.id,t.operations.audit().size());assertThat(t.revision).isEqualTo(revision);
  assertThatThrownBy(()->notifications.read(player,t.id,Long.MAX_VALUE)).hasMessage("INVALID_NOTIFICATION_CURSOR");assertThatThrownBy(()->notifications.read(outsider,t.id,through)).hasMessage("TOURNAMENT_FORBIDDEN");
  var stored=repository.all().stream().filter(row->row.id().toString().equals(t.id)).findFirst().orElseThrow();assertThat(stored.details().notificationReads()).containsEntry(a.id().value().toString(),visible).doesNotContainKey(b.id().value().toString());var restored=new Tournament(t.id,t.name,t.format,t.createdAt);stored.details().restore(restored);assertThat(restored.notificationReads).isEqualTo(t.notificationReads);
  assertThat(notifications.feed(outsider).get("items").toString()).isEqualTo("[]");
 }
}
