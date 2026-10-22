package com.rpsbattle.tournament.registration;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.tournament.timeline.TimelineRegistrationStore;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Real independent DB transactions deliberately bypass the single-JVM tournament monitor. */
@SpringBootTest(properties={"rps.bots.enabled=false","spring.datasource.hikari.maximum-pool-size=10","rps.bots.directory=${java.io.tmpdir}/rps-capacity-${random.uuid}"})
@Testcontainers(disabledWithoutDocker=true)
@org.springframework.context.annotation.Import(RegistrationCapacityPostgresTest.QueryStatistics.class)
class RegistrationCapacityPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @Autowired TimelineRegistrationStore store;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    static final Instant NOW=Instant.parse("2026-10-21T12:00:00Z");
    final EligibilityDecision eligible=new EligibilityDecision(EligibilityDecision.Decision.APPROVE,List.of());
    UUID user(){var id=UUID.randomUUID();var name="capacity_"+id.toString().substring(0,8);db.update("insert into users(id,username,email,password_hash,display_name) values(?,?,?,?,?)",id,name,name+"@example.com","$2a$12$fixture.hash",name);return id;}
    UUID cup(){var id=UUID.randomUUID();db.update("insert into tournaments(id,owner_user_id,name,format,status,max_participants) values(?,?,'Capacity transaction','ROUND_ROBIN','REGISTRATION',2)",id,user());return id;}
    TimelineRegistrationStore.View apply(UUID cup,UUID user){return store.register(cup,user,"Capacity person","CPP",null,2,NOW,eligible);}
    long approved(UUID cup){return db.queryForObject("select count(*) from tournament_timeline_registrations where tournament_id=? and status='APPROVED'",Long.class,cup);}
    List<String> race(List<Callable<String>> tasks)throws Exception{
        var ready=new CountDownLatch(tasks.size());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(tasks.size())){
            var pending=tasks.stream().map(task->pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timed out");return task.call();})).toList();
            try{assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();}finally{start.countDown();}
            var result=new ArrayList<String>();for(var future:pending)result.add(future.get(20,TimeUnit.SECONDS));return result;
        }
    }
    @Test void concurrentLastSlotApprovesOnlyOneAndAssignsUniqueOrderedWaitlist()throws Exception{
        UUID cup=cup();apply(cup,user());var applicants=IntStream.range(0,8).mapToObj(i->user()).toList();
        var results=race(applicants.stream().<Callable<String>>map(user->()->apply(cup,user).status()).toList());
        assertThat(results).filteredOn("APPROVED"::equals).hasSize(1);assertThat(results).filteredOn("WAITLISTED"::equals).hasSize(7);assertThat(approved(cup)).isEqualTo(2);
        assertThat(applicants.stream().map(user->store.mine(cup,user)).filter(r->r.status().equals("WAITLISTED")).map(TimelineRegistrationStore.View::waitlistPosition).sorted().toList()).containsExactly(1L,2L,3L,4L,5L,6L,7L);
    }
    @Test void concurrentWaitlistApprovalsCannotBothClaimTheSameFreedPlace()throws Exception{
        UUID cup=cup(),departing=user();apply(cup,departing);apply(cup,user());var a=apply(cup,user());var b=apply(cup,user());store.withdraw(cup,departing);
        UUID actor=user();var results=race(List.of(()->review(cup,a.id(),actor),()->review(cup,b.id(),actor)));
        assertThat(results).containsExactlyInAnyOrder("APPROVED","CAPACITY_FULL");assertThat(approved(cup)).isEqualTo(2);assertThat(store.page(cup,0,"WAITLISTED")).hasSize(1);assertThat(store.page(cup,0,"WAITLISTED").getFirst().waitlistPosition()).isEqualTo(1);
    }
    String review(UUID cup,UUID id,UUID actor){try{return store.review(cup,id,"APPROVED","A place is available",actor,NOW,2).after().status();}catch(AuthException error){return error.code();}}
    @Test void concurrentDuplicateSubmissionsKeepOneApplication()throws Exception{
        UUID cup=cup(),sameUser=user();var results=race(List.of(()->submit(cup,sameUser),()->submit(cup,sameUser)));
        assertThat(results).containsExactlyInAnyOrder("APPROVED","DUPLICATE_REGISTRATION");assertThat(store.page(cup,0,null)).hasSize(1);
    }
    String submit(UUID cup,UUID user){try{return apply(cup,user).status();}catch(AuthException error){return error.code();}}
    @Test void staleCancelledApplicationInPersistenceContextCannotBypassDuplicateCheck()throws Exception{
        UUID cup=cup(),sameUser=user();apply(cup,sameUser);store.withdraw(cup,sameUser);var cached=new CyclicBarrier(2);
        Callable<String> submit=()->{
            try{return new TransactionTemplate(transactions).execute(status->{
                assertThat(store.mine(cup,sameUser).status()).isEqualTo("CANCELLED");
                try{cached.await(10,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException(failure);}
                return apply(cup,sameUser).status();
            });}catch(AuthException error){return error.code();}
        };
        assertThat(race(List.of(submit,submit))).containsExactlyInAnyOrder("APPROVED","DUPLICATE_REGISTRATION");assertThat(approved(cup)).isEqualTo(1);
    }
    @Test void concurrentAutomaticPromotionsAllocateOnlyOneFreedPlace()throws Exception{
        UUID cup=cup(),departing=user();store.enable(cup);apply(cup,departing);apply(cup,user());var first=apply(cup,user());var second=apply(cup,user());store.withdraw(cup,departing);
        Callable<String> promotion=()->Integer.toString(store.promote(cup,new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,false),Set.of("CPP"),NOW).size());
        assertThat(race(List.of(promotion,promotion))).containsExactlyInAnyOrder("1","0");assertThat(approved(cup)).isEqualTo(2);assertThat(store.page(cup,0,"WAITLISTED").getFirst().id()).isEqualTo(second.id());assertThat(store.page(cup,0,"APPROVED")).anyMatch(r->r.id().equals(first.id()));
    }
    @Test void promotionRechecksAccountAndSkipsUnresolvedVerification(){
        UUID cup=cup(),departing=user();apply(cup,departing);apply(cup,user());UUID banned=user();var invalid=apply(cup,banned);var valid=apply(cup,user());db.update("update users set status='DISABLED' where id=?",banned);store.withdraw(cup,departing);
        var results=store.promote(cup,new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,false),Set.of("CPP"),NOW);assertThat(results).hasSize(2);assertThat(results).anyMatch(r->r.applicationId().equals(invalid.id())&&r.after().equals("REJECTED"));assertThat(results).anyMatch(r->r.applicationId().equals(valid.id())&&r.after().equals("APPROVED"));assertThat(approved(cup)).isEqualTo(2);
        UUID other=cup(),otherDeparting=user(),unverified=user();apply(other,otherDeparting);apply(other,user());store.register(other,unverified,"Unverified affiliation","CPP","UET",2,NOW,eligible);store.withdraw(other,otherDeparting);
        assertThat(store.promote(other,new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,true),Set.of("CPP"),NOW)).isEmpty();assertThat(store.mine(other,unverified).status()).isEqualTo("WAITLISTED");assertThat(approved(other)).isEqualTo(1);
    }
    @Test void closedRegistrationRemainsReviewableButLockedRosterRejectsAllChanges(){
        UUID cup=cup(),owner=user();var first=apply(cup,owner);apply(cup,user());var waiting=apply(cup,user());store.enable(cup);store.close(cup,NOW);
        assertThat(store.control(cup).lockedAt()).isNull();assertThatThrownBy(()->apply(cup,user())).isInstanceOf(AuthException.class).hasMessage("REGISTRATION_NOT_OPEN");
        var state=store.lockRoster(cup,owner,"Organizer",NOW,2,NOW.plusSeconds(60));assertThat(state.lockedCount()).isEqualTo(2);assertThat(store.control(cup).lockedAt()).isNotNull();
        assertThatThrownBy(()->store.withdraw(cup,owner)).hasMessage("ROSTER_LOCKED");assertThatThrownBy(()->store.review(cup,waiting.id(),"APPROVED","No changes",owner,NOW,2)).hasMessage("ROSTER_LOCKED");assertThat(store.promote(cup,new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,false),Set.of("CPP"),NOW)).isEmpty();
    }
    @Test void unresolvedApplicationsBlockLockAndRollbackLeavesControlUnlocked(){
        UUID cup=cup(),owner=user();apply(cup,owner);apply(cup,user());store.register(cup,user(),"Needs review","CPP",null,2,NOW);store.enable(cup);store.close(cup,NOW);
        assertThatThrownBy(()->store.lockRoster(cup,owner,"Organizer",NOW,2,NOW.plusSeconds(60))).hasMessage("ROSTER_REVIEW_REQUIRED");assertThat(store.control(cup).lockedAt()).isNull();assertThat(store.control(cup).lockedAt()).isNull();
    }
    @Test void promotionRollsBackWithTheWithdrawalThatFreedItsPlace(){
        UUID cup=cup(),departing=user(),waitingUser=user();apply(cup,departing);apply(cup,user());apply(cup,waitingUser);
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{store.withdraw(cup,departing);store.promote(cup,new RegistrationPolicy(RegistrationApprovalPolicy.HYBRID,false),Set.of("CPP"),NOW);throw new IllegalStateException("Abort promotion");})).hasMessage("Abort promotion");assertThat(store.mine(cup,departing).status()).isEqualTo("APPROVED");assertThat(store.mine(cup,waitingUser).status()).isEqualTo("WAITLISTED");
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class QueryStatistics {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer registrationQueryCounter(){return properties->properties.put("hibernate.session_factory.statement_inspector",new QueryCounter());}
    }
    static class QueryCounter implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<Integer> statements=ThreadLocal.withInitial(()->0);
        @Override public String inspect(String sql){statements.set(statements.get()+1);return sql;}
    }
    @Test void waitlistPageUsesTwoQueriesInsteadOfOnePositionQueryPerApplication(){
        UUID cup=cup();apply(cup,user());apply(cup,user());for(int i=0;i<110;i++)apply(cup,user());
        QueryCounter.statements.set(0);
        var page=store.page(cup,new com.rpsbattle.tournament.timeline.ApplicationFilter(0,100,"WAITLISTED",null,null,null,null,null,null));
        assertThat(page).hasSize(100);assertThat(page.stream().map(TimelineRegistrationStore.Application::waitlistPosition).toList()).containsExactlyElementsOf(IntStream.rangeClosed(1,100).mapToObj(i->(long)i).toList());assertThat(QueryCounter.statements.get()).isLessThanOrEqualTo(2);
        QueryCounter.statements.set(0);store.page(cup,new com.rpsbattle.tournament.timeline.ApplicationFilter(0,100,"APPROVED",null,null,null,null,null,null));assertThat(QueryCounter.statements.get()).isEqualTo(1);
    }
    @Test void concurrentRosterLockAndWithdrawalNeverLeaveAnInvalidLockedRoster()throws Exception{
        UUID cup=cup(),owner=user();apply(cup,owner);apply(cup,user());store.enable(cup);store.close(cup,NOW);
        var results=race(List.of(()->{try{store.lockRoster(cup,owner,"Organizer",NOW,2,NOW.plusSeconds(60));return "LOCKED";}catch(AuthException e){return e.code();}},()->{try{store.withdraw(cup,owner);return "WITHDRAWN";}catch(AuthException e){return e.code();}}));
        var control=store.control(cup);
        if(control.lockedAt()!=null){assertThat(results).containsExactlyInAnyOrder("LOCKED","ROSTER_LOCKED");assertThat(approved(cup)).isEqualTo(2);assertThat(control.lockedCount()).isEqualTo(2);}
        else{assertThat(results).containsExactlyInAnyOrder("NOT_ENOUGH_PARTICIPANTS","WITHDRAWN");assertThat(approved(cup)).isEqualTo(1);}
    }
    @Test void cachedReviewCannotAffectAWithdrawnAndRejoinedApplication()throws Exception{
        UUID cup=cup(),person=user(),actor=user();var original=apply(cup,person);var cached=new CountDownLatch(1);var rejoined=new CountDownLatch(1);
        var result=race(List.of(()->{try{return new TransactionTemplate(transactions).execute(status->{store.mine(cup,person);cached.countDown();try{if(!rejoined.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Rejoin timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return store.review(cup,original.id(),"REJECTED","Old snapshot",actor,NOW,2,"APPROVED",original.registeredAt()).after().status();});}catch(AuthException e){return e.code();}},()->{if(!cached.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Cache timeout");try{store.withdraw(cup,person);store.register(cup,person,"Rejoined","CPP",null,2,NOW.plusSeconds(1),eligible);return "REJOINED";}finally{rejoined.countDown();}}));
        assertThat(result).containsExactlyInAnyOrder("APPLICATION_CHANGED","REJOINED");assertThat(store.mine(cup,person).status()).isEqualTo("APPROVED");
    }
    @Test void failedReviewRollsBackPublicMessageTogetherWithStatus(){
        UUID cup=cup(),person=user(),actor=user();var application=apply(cup,person);store.publicMessage(cup,application.id(),"Original public message");
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{store.review(cup,application.id(),"REJECTED","Internal reason",actor,NOW,2);store.publicMessage(cup,application.id(),"New message");throw new IllegalStateException("Abort review");})).hasMessage("Abort review");
        var after=store.mine(cup,person);assertThat(after.status()).isEqualTo("APPROVED");assertThat(after.publicMessage()).isEqualTo("Original public message");
    }
    @Test void rollbackReleasesAllocationAndWaitingOrderAndCapacityComesFromDatabase(){
        UUID cup=cup();apply(cup,user());apply(cup,user());var tx=new TransactionTemplate(transactions);UUID rolledBack=user();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{apply(cup,rolledBack);throw new IllegalStateException("Rollback fixture");})).isInstanceOf(IllegalStateException.class);
        assertThat(store.mine(cup,rolledBack)).isNull();assertThat(apply(cup,user()).waitlistPosition()).isEqualTo(1);
        assertThatThrownBy(()->store.register(cup,user(),"Stale capacity","CPP",null,4000,NOW,eligible)).isInstanceOf(AuthException.class).hasMessage("STALE_TOURNAMENT_REVISION");assertThat(approved(cup)).isEqualTo(2);
    }
}
