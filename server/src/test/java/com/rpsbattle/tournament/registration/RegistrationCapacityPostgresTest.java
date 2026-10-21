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
    @Test void rollbackReleasesAllocationAndWaitingOrderAndCapacityComesFromDatabase(){
        UUID cup=cup();apply(cup,user());apply(cup,user());var tx=new TransactionTemplate(transactions);UUID rolledBack=user();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{apply(cup,rolledBack);throw new IllegalStateException("Rollback fixture");})).isInstanceOf(IllegalStateException.class);
        assertThat(store.mine(cup,rolledBack)).isNull();assertThat(apply(cup,user()).waitlistPosition()).isEqualTo(1);
        assertThatThrownBy(()->store.register(cup,user(),"Stale capacity","CPP",null,4000,NOW,eligible)).isInstanceOf(AuthException.class).hasMessage("STALE_TOURNAMENT_REVISION");assertThat(approved(cup)).isEqualTo(2);
    }
}
