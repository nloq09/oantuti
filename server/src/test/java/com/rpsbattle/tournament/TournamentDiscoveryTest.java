package com.rpsbattle.tournament;
import com.rpsbattle.account.domain.*;import com.rpsbattle.account.repository.UserRepository;import com.rpsbattle.auth.*;import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import java.time.Instant;import java.util.*;import static org.assertj.core.api.Assertions.*;
@SpringBootTest class TournamentDiscoveryTest {
 @Autowired TournamentDiscoveryService discovery;@Autowired OwnedTournamentService owned;@Autowired UserRepository users;
 @Test void discoveryIsBoundedPublicMetadataAndJoinedUsesAuthenticatedIdentity(){
  var now=Instant.now();String name="hub_"+UUID.randomUUID().toString().substring(0,8);var user=users.save(new User(UserId.random(),name,name+"@example.invalid","not-for-login",name,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));var auth=AuthService.sessionAuthentication(user);
  var t=owned.create(auth,"Hub fixture "+name,TournamentFormat.ROUND_ROBIN,"PUBLIC");owned.openRegistration(auth,t.id);
  var page=discovery.discover(null,"open",name,null,0,12);assertThat(page.get("total")).isEqualTo(1L);assertThat(page.toString()).contains("organizer","registrationDeadline").doesNotContain("botToken","ownerToken","matches=","not-for-login");
  assertThat(((Map<?,?>)((List<?>)page.get("items")).getFirst()).get("owned")).isEqualTo(false);
  var ownPage=discovery.discover(auth,"open",name,null,0,12);
  assertThat(((Map<?,?>)((List<?>)ownPage.get("items")).getFirst()).get("owned")).isEqualTo(true);
  assertThat(discovery.discover(null,"open","%' or 1=1 --",null,0,12).get("total")).isEqualTo(0L);
  assertThatThrownBy(()->discovery.discover(null,"joined",null,null,0,12)).isInstanceOf(AuthException.class);
  assertThat(discovery.discover(auth,"joined",name,null,0,12).get("total")).isEqualTo(0L);
  assertThatThrownBy(()->discovery.discover(auth,"open",null,null,0,51)).isInstanceOf(AuthException.class);
 }

 @Test void timelineCategoriesUseServerWindowsBeforeCountingAndPagination(){
  var now=Instant.now();String key="phase_"+UUID.randomUUID().toString().substring(0,8);var user=users.save(new User(UserId.random(),key,key+"@example.invalid","unused",key,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));var auth=AuthService.sessionAuthentication(user);
  for(int i=0;i<9;i++){var t=owned.create(auth,key+" "+i,TournamentFormat.ROUND_ROBIN,"PUBLIC");var open=now.plusSeconds(i<3?3600:i<6?-30:-120);var times=new TournamentTimeline(open,open.plusSeconds(60),open.plusSeconds(120),open.plusSeconds(180),open.plusSeconds(190),open.plusSeconds(210),open.plusSeconds(240),open.plusSeconds(270),open.plusSeconds(300),open.plusSeconds(360),null);owned.configureTimeline(auth,t.id,t.revision,times);try{owned.clock(java.time.Clock.fixed(open,java.time.ZoneOffset.UTC));owned.openRegistration(auth,t.id);}finally{owned.clock(java.time.Clock.systemUTC());}}
  assertThat(discovery.discover(null,"open",key,null,0,2).get("total")).isEqualTo(3L);assertThat((List<?>)discovery.discover(null,"open",key,null,1,2).get("items")).hasSize(1);assertThat(discovery.discover(null,"upcoming",key,null,0,2).get("total")).isEqualTo(6L);
  var fixture=new Tournament(UUID.randomUUID().toString(),"Boundary",TournamentFormat.ROUND_ROBIN,now);fixture.status=TournamentStatus.REGISTRATION;fixture.timeline=TournamentTimelineTest.timeline();assertThat(TournamentDiscoveryService.discoveryCategory(fixture,TournamentTimelineTest.START.minusNanos(1))).isEqualTo("upcoming");assertThat(TournamentDiscoveryService.discoveryCategory(fixture,TournamentTimelineTest.START)).isEqualTo("open");assertThat(TournamentDiscoveryService.discoveryCategory(fixture,TournamentTimelineTest.START.plusSeconds(60))).isEqualTo("upcoming");
 }
 @Test void myTournamentsFiltersByAuthenticatedOwnerBeforeCountingAndPaging(){
  var now=Instant.now();String key="mine_"+UUID.randomUUID().toString().substring(0,8);
  var owner=users.save(new User(UserId.random(),key,key+"@example.invalid","unused",key,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));
  var other=users.save(new User(UserId.random(),key+"_other",key+"_other@example.invalid","unused","Other",UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));
  var auth=AuthService.sessionAuthentication(owner);var otherAuth=AuthService.sessionAuthentication(other);
  owned.create(auth,key+" draft",TournamentFormat.ROUND_ROBIN,"PUBLIC");
  var open=owned.create(auth,key+" open",TournamentFormat.ROUND_ROBIN,"PUBLIC");owned.openRegistration(auth,open.id);
  owned.create(otherAuth,key+" other",TournamentFormat.SWISS,"PUBLIC");
  var first=discovery.discover(auth,"owned",key,null,0,1);
  assertThat(first.get("total")).isEqualTo(2L);assertThat((List<?>)first.get("items")).hasSize(1);
  assertThat(((Map<?,?>)((List<?>)first.get("items")).getFirst()).get("owned")).isEqualTo(true);
  assertThat((List<?>)discovery.discover(auth,"owned",key,null,1,1).get("items")).hasSize(1);
  assertThat(discovery.discover(auth,"owned",key,"SWISS",0,12).get("total")).isEqualTo(0L);
  assertThat(discovery.discover(otherAuth,"owned",key,null,0,12).get("total")).isEqualTo(1L);
  assertThatThrownBy(()->discovery.discover(null,"owned",key,null,0,12)).isInstanceOf(AuthException.class);
 }
 @Test void manualDeadlineClosesDiscoveryAdmissionAtExactServerBoundary(){
  var now=Instant.now();String key="manual_"+UUID.randomUUID().toString().substring(0,8);var user=users.save(new User(UserId.random(),key,key+"@example.invalid","unused",key,UserRole.USER,AccountStatus.ACTIVE,now,now,null,0));var auth=AuthService.sessionAuthentication(user);
  var t=owned.create(auth,key,TournamentFormat.ROUND_ROBIN,"PUBLIC");var deadline=now.plusSeconds(60);owned.configureTiming(auth,t.id,t.revision,new TournamentTiming(deadline,null,null));owned.openRegistration(auth,t.id);
  var previous=discovery.clock;try{discovery.clock=java.time.Clock.fixed(deadline.minusNanos(1),java.time.ZoneOffset.UTC);assertThat(discovery.discover(null,"open",key,null,0,12).get("total")).isEqualTo(1L);discovery.clock=java.time.Clock.fixed(deadline,java.time.ZoneOffset.UTC);assertThat(discovery.discover(null,"open",key,null,0,12).get("total")).isEqualTo(0L);assertThat(discovery.discover(null,"upcoming",key,null,0,12).get("total")).isEqualTo(1L);}finally{discovery.clock=previous;}
 }
}
