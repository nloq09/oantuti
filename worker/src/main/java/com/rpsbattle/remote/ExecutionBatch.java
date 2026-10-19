package com.rpsbattle.remote;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit single batch until Central result acknowledgement is implemented. */
public final class ExecutionBatch {
    private final CentralClient central;
    private final IdentityStore.Identity identity;
    private final WorkerConfig config;
    private final ArtifactCache cache;
    private final RuntimeProbe probe;
    private final PrintStream log;
    private final AtomicInteger active=new AtomicInteger(),bots=new AtomicInteger();
    private final Set<SandboxBot> children=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    public ExecutionBatch(CentralClient central,IdentityStore.Identity identity,WorkerConfig config,ArtifactCache cache,RuntimeProbe probe,PrintStream log) {
        this.central=central;this.identity=identity;this.config=config;this.cache=cache;this.probe=probe;this.log=log;
    }
    public int run()throws IOException,InterruptedException {
        var journal=new ExecutionJournal(config.stateDirectory().resolve("executions"));
        if(journal.pending()||!central.assigned(identity).path("jobs").isEmpty()){
            log.println("Pending execution state exists; automatic replay/reclaim is blocked until result/recovery support is enabled.");return 1;
        }
        var heartbeat=new HeartbeatAgent(central,identity,probe.capabilities(),()->{
            var sample=new HashMap<>(probe.heartbeat());sample.put("activeMatches",active.get());sample.put("activeBotProcesses",bots.get());
            sample.put("freeSlots",Math.max(0,config.maxMatches()-active.get()));sample.put("cacheSizeMb",cache.stats().bytes()/1048576);return sample;
        },log);
        heartbeat.tick();long epoch=heartbeat.epoch();
        var claims=new ClaimClient(central,identity);
        var response=claims.claim(claims.request(epoch,Math.min(16,config.maxMatches()),cache.hashes(),"1","classic-online-v1"));
        var jobs=response.path("jobs");if(jobs.isEmpty()){log.println("No compatible remote jobs available.");return 0;}
        var timer=Executors.newScheduledThreadPool(2);var pool=Executors.newFixedThreadPool(Math.min(16,config.maxMatches()));
        Thread shutdown=new Thread(()->{cancelled.set(true);children.forEach(SandboxBot::close);pool.shutdownNow();},"worker-execution-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            timer.scheduleWithFixedDelay(()->{
                try{heartbeat.tick();if(heartbeat.epoch()!=epoch){cancelled.set(true);children.forEach(SandboxBot::close);}}
                catch(CentralClient.CentralFailure e){if(Set.of(400,401,403,404,409).contains(e.status)){cancelled.set(true);children.forEach(SandboxBot::close);}}
                catch(IOException e){/* bounded by match deadline; no new claims during a network interruption */}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
            },5,5,TimeUnit.SECONDS);
            var tasks=new ArrayList<Future<Boolean>>();
            for(var node:jobs){var job=ExecutionJob.parse(node,identity.workerId(),epoch);tasks.add(pool.submit(()->execute(job,node,journal,timer)));}
            int failed=0;for(var task:tasks)try{if(!task.get())failed++;}catch(ExecutionException e){failed++;}
            log.println("Batch prepared locally: "+(tasks.size()-failed)+" finished, "+failed+" failed. Results await Central upload support; reservations retained.");
            return failed==0?0:1;
        }finally{
            cancelled.set(true);children.forEach(SandboxBot::close);pool.shutdownNow();timer.shutdownNow();
            pool.awaitTermination(10,TimeUnit.SECONDS);timer.awaitTermination(5,TimeUnit.SECONDS);
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
    }
    private boolean execute(ExecutionJob job,com.fasterxml.jackson.databind.JsonNode node,ExecutionJournal journal,ScheduledExecutorService timer) {
        Path directory=null;SandboxBot a=null,b=null;ScheduledFuture<?> watchdog=null;long deadline=Long.MAX_VALUE;
        active.incrementAndGet();
        try {
            var client=new ArtifactClient(central,identity,cache);
            try(var artifactA=client.acquire(job.executionId(),job.match().botA());var artifactB=client.acquire(job.executionId(),job.match().botB())) {
                if(cancelled.get()||!java.time.Instant.now().plusMillis(job.match().matchHardTimeoutMs()).isBefore(job.leaseUntil()))throw new IOException("Execution unavailable");
                directory=journal.begin(job,node);
                Path stagedA=journal.stage(directory,"A",artifactA.path(),job.match().botA()),stagedB=journal.stage(directory,"B",artifactB.path(),job.match().botB());
                var started=central.post(identity,"jobs/"+job.jobId()+"/start",Map.of("executionId",job.executionId(),"epoch",job.epoch()));
                if(!started.path("executionId").asText().equals(job.executionId().toString())||!"RUNNING".equals(started.path("status").asText()))throw new IOException("Invalid start acknowledgement");
                deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(job.match().matchHardTimeoutMs());
                a=new SandboxBot(stagedA,job.match().botA(),job.executionId(),deadline);b=new SandboxBot(stagedB,job.match().botB(),job.executionId(),deadline);
                children.add(a);children.add(b);bots.addAndGet(2);var first=a;var second=b;
                watchdog=timer.schedule(()->{first.close();second.close();},job.match().matchHardTimeoutMs(),TimeUnit.MILLISECONDS);
                if(cancelled.get())throw new IOException("Execution cancelled");
                var result=new HeadlessMatch().run(job.match(),a,b,deadline);
                if(cancelled.get())throw new IOException("Execution cancelled");journal.finish(directory,job,result);return true;
            }
        }catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            if(directory!=null)try{journal.failure(directory,job,System.nanoTime()>=deadline?"MATCH_HARD_TIMEOUT":e instanceof com.rpsbattle.bot.BotInfrastructureException?"SANDBOX_UNAVAILABLE":"EXECUTION_FAILED");}catch(IOException ignored){}
            // Started or ambiguous requests retain the reservation. Never decline a possibly running match.
            if(directory==null)try{new ClaimClient(central,identity).decline(job.jobId(),job.executionId(),"ARTIFACT_UNAVAILABLE");}catch(Exception ignored){}
            return false;
        }finally{
            if(watchdog!=null)watchdog.cancel(false);
            if(a!=null){a.close();b.close();children.remove(a);children.remove(b);bots.addAndGet(-2);}active.decrementAndGet();
            if(directory!=null)try{journal.clearStaged(directory);}catch(IOException ignored){}
        }
    }
}
