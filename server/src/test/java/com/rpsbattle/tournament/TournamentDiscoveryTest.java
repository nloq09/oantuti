package com.rpsbattle.tournament;
import com.rpsbattle.account.domain.*;import com.rpsbattle.account.repository.UserRepository;import com.rpsbattle.auth.*;import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import java.time.Instant;import java.util.*;import static org.assertj.core.api.Assertions.*;
@SpringBootTest class TournamentDiscoveryTest {
 @Autowired TournamentDiscoveryService discovery;@Autowired OwnedTournamentService owned;@Autowired UserRepository users;
 @Test void discoveryIsBoundedPublicMetadataAndJoinedUsesAuthenticatedIdentity(){
  var now=Instant.now();String name="hub_"+UUID.randomUUID().toString().substring(0,8);var user=users.save(new User(UserId.random(),name,name+"@example.invalid","not-for-login",name,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));var auth=AuthService.sessionAuthentication(user);
  var t=owned.create(auth,"Hub fixture "+name,TournamentFormat.ROUND_ROBIN,"PUBLIC");owned.openRegistration(auth,t.id);
  var page=discovery.discover(null,"open",name,null,0,12);assertThat(page.get("total")).isEqualTo(1L);assertThat(page.toString()).contains("organizer","registrationDeadline").doesNotContain("botToken","ownerToken","matches=","not-for-login");
  assertThat(discovery.discover(null,"open","%' or 1=1 --",null,0,12).get("total")).isEqualTo(0L);
  assertThatThrownBy(()->discovery.discover(null,"joined",null,null,0,12)).isInstanceOf(AuthException.class);
  assertThat(discovery.discover(auth,"joined",name,null,0,12).get("total")).isEqualTo(0L);
  assertThatThrownBy(()->discovery.discover(auth,"open",null,null,0,51)).isInstanceOf(AuthException.class);
 }
}
