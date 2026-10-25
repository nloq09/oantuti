package com.rpsbattle.worker;

import com.rpsbattle.tournament.TournamentService;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.online.DemoRequestException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class WorkerManagerTest {
    @Test void localTournamentQuotaDoesNotBlockRemoteAdmissionForTheSameTournament(){
        var routing=mock(com.rpsbattle.worker.remote.RemoteRoutingService.class);var cup=tournament();cup.maxConcurrentMatches=1;
        var local=request(cup,"local");var remote=request(cup,"remote");when(routing.route(cup,remote.match())).thenReturn(true);
        var calls=new AtomicInteger();var manager=new WorkerManager(new ExecutionQueue(10),r->{calls.incrementAndGet();return new MatchExecutionResult("room","archive");},mock(TournamentService.class),2,Runnable::run);manager.routing(routing);
        manager.offer(local);manager.offer(remote);manager.dispatch();
        verify(routing).route(cup,remote.match());assertThat(calls.get()).isEqualTo(1);assertThat(manager.active()).isEqualTo(1);assertThat(manager.pending()).isZero();manager.drain();
    }
    @Test void saturatedLocalMatchesDoNotBlockRemoteAdmissionFromAnotherTournament(){
        var routing=mock(com.rpsbattle.worker.remote.RemoteRoutingService.class);var local=request(tournament(),"local");var remote=request(tournament(),"remote");
        when(routing.route(remote.tournament(),remote.match())).thenReturn(true);
        var calls=new AtomicInteger();var manager=new WorkerManager(new ExecutionQueue(10),r->{calls.incrementAndGet();return new MatchExecutionResult("room","archive");},mock(TournamentService.class),1,Runnable::run);manager.routing(routing);
        manager.offer(local);manager.dispatch();manager.offer(remote);manager.dispatch();
        verify(routing).route(remote.tournament(),remote.match());assertThat(calls.get()).isEqualTo(1);assertThat(manager.active()).isEqualTo(1);assertThat(manager.pending()).isZero();manager.drain();
    }
    @Test void unavailableRemoteWorkerDefersLocalFallbackWithoutLosingItsReservationOrExceedingLocalCapacity(){
        var routing=mock(com.rpsbattle.worker.remote.RemoteRoutingService.class);var calls=new AtomicInteger();var queue=new ExecutionQueue(2);
        var manager=new WorkerManager(queue,r->{calls.incrementAndGet();return new MatchExecutionResult("room","archive");},mock(TournamentService.class),1,Runnable::run);manager.routing(routing);
        var local=request(tournament(),"local");var waiting=request(tournament(),"waiting");manager.offer(local);manager.dispatch();manager.offer(waiting);manager.dispatch();manager.offer(waiting);manager.dispatch();
        assertThat(calls.get()).isEqualTo(1);assertThat(manager.active()).isEqualTo(1);assertThat(queue.reservedIds()).containsExactly("waiting");
        manager.complete("local");manager.dispatch();assertThat(calls.get()).isEqualTo(2);assertThat(waiting.match().status).isEqualTo(TournamentMatch.Status.RUNNING);manager.drain();
    }
    private MatchExecutionRequest request(Tournament t,String id) {
        var a=new TournamentParticipant("a","a","secret","a",1L);
        var b=new TournamentParticipant("b","b","secret","b",2L);
        var m=new TournamentMatch(id,t.id,a,b);t.matches.add(m);return new MatchExecutionRequest(t,m);
    }
    private Tournament tournament(){var t=new Tournament(UUID.randomUUID().toString(),"test",TournamentFormat.ROUND_ROBIN,java.time.Instant.now());t.status=TournamentStatus.RUNNING;return t;}
    @Test void batchAdmissionIsAtomicAndOverloadDoesNotDiscardAcceptedWork() {
        var q=new ExecutionQueue(2);q.reserve(List.of("a","b"));
        assertThatThrownBy(()->q.reserve(List.of("c","d"))).isInstanceOf(DemoRequestException.class).hasMessage("SERVER_BUSY");
        assertThat(q.size()).isEqualTo(2);
        q.release(List.of("a","b"));q.reserve(List.of("c","d"));assertThat(q.size()).isEqualTo(2);
        q.drain();assertThatThrownBy(()->q.reserve(List.of("x"))).hasMessage("SERVER_BUSY");
    }
    @Test void holdsCapacityUntilMatchCompletionAndDeduplicatesRepeatedSchedulerOffers() {
        var q=new ExecutionQueue(10);var calls=new AtomicInteger();var tasks=new ArrayDeque<Runnable>();
        var manager=new WorkerManager(q,r->{calls.incrementAndGet();return new MatchExecutionResult(r.match().id,"archive");},mock(TournamentService.class),1,tasks::add);
        var first=request(tournament(),"first");var second=request(tournament(),"second");
        manager.offer(first);manager.offer(first);manager.offer(second);manager.dispatch();
        assertThat(manager.active()).isEqualTo(1);assertThat(q.size()).isEqualTo(1);assertThat(calls.get()).isZero();
        tasks.remove().run();manager.dispatch();assertThat(calls.get()).isEqualTo(1);assertThat(tasks).isEmpty();
        manager.complete("first");manager.dispatch();tasks.remove().run();assertThat(calls.get()).isEqualTo(2);
        assertThat(second.match().status).isEqualTo(TournamentMatch.Status.RUNNING);
        manager.drain();
    }
    @Test void queuedCancellationAndFailedLaunchFreeSlotsForOtherTournaments() {
        var tasks=new ArrayDeque<Runnable>();var worker=mock(MatchWorker.class);
        var manager=new WorkerManager(new ExecutionQueue(10),worker,mock(TournamentService.class),1,tasks::add);
        var first=request(tournament(),"first");var second=request(tournament(),"second");
        manager.offer(first);manager.offer(second);manager.dispatch();first.tournament().status=TournamentStatus.CANCELLED;tasks.remove().run();
        verifyNoInteractions(worker);assertThat(manager.active()).isZero();
        when(worker.execute(second)).thenThrow(new IllegalStateException("private diagnostic"));
        manager.dispatch();tasks.remove().run();assertThat(second.match().failure).isEqualTo("MATCH_START_FAILED");assertThat(manager.active()).isZero();
    }
    @Test void tournamentLimitIsEnforcedAtLaunchEvenWhenMultipleRequestsWereQueued() {
        var tasks=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
        var manager=new WorkerManager(new ExecutionQueue(10),r->{calls.incrementAndGet();return new MatchExecutionResult(r.match().id,"archive");},mock(TournamentService.class),4,tasks::add);
        var cup=tournament();cup.maxConcurrentMatches=1;var first=request(cup,"one");var second=request(cup,"two");var other=request(tournament(),"other");
        manager.offer(first);manager.offer(second);manager.offer(other);manager.dispatch();while(!tasks.isEmpty())tasks.remove().run();
        assertThat(calls.get()).isEqualTo(2);assertThat(second.match().status).isEqualTo(TournamentMatch.Status.PENDING);assertThat(manager.active()).isEqualTo(2);
        first.match().status=TournamentMatch.Status.FINISHED;manager.complete(first.match().id);manager.offer(second);manager.dispatch();tasks.remove().run();assertThat(calls.get()).isEqualTo(3);
    }
    @Test void dispatchDoesNotRunSlowWorkerOnCallerAndDrainPreventsFurtherLaunches() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var caller=Thread.currentThread();var thread=new java.util.concurrent.atomic.AtomicReference<Thread>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var manager=new WorkerManager(new ExecutionQueue(10),r->{thread.set(Thread.currentThread());entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return new MatchExecutionResult("room","archive");},mock(TournamentService.class),1,pool);
            manager.offer(request(tournament(),"first"));manager.dispatch();
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();assertThat(thread.get()).isNotSameAs(caller);
            release.countDown();manager.drain();assertThat(manager.status().get("state")).isEqualTo("DRAINING");assertThat(manager.pending()).isZero();
        }
    }
}
