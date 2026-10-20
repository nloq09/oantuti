package com.rpsbattle.remote;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit bounded batch with durable results and fenced lease recovery. */
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
    private final Set<UUID> held=ConcurrentHashMap.newKeySet(),lost=ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID,Set<SandboxBot>> processes=new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,java.time.Instant> leases=new ConcurrentHashMap<>();
    public ExecutionBatch(CentralClient central,IdentityStore.Identity identity,WorkerConfig config,ArtifactCache cache,RuntimeProbe probe,PrintStream log) {
        this.central=central;this.identity=identity;this.config=config;this.cache=cache;this.probe=probe;this.log=log;
    }
    public int run()throws IOException,InterruptedException {
        var journal=new ExecutionJournal(config.stateDirectory().resolve("executions"));
        new RecoveryAgent(central,identity,journal).reconcile(true);
        if(journal.pending()||!central.assigned(identity).path("jobs").isEmpty()){
            log.println("Unresolved execution state exists; automatic replay/reclaim is blocked. Keep state for recovery.");return 1;
        }
        var heartbeat=new HeartbeatAgent(central,identity,probe.capabilities(),()->{
            var sample=new HashMap<>(probe.heartbeat());sample.put("activeMatches",active.get());sample.put("activeBotProcesses",bots.get());
            sample.put("freeSlots",Math.max(0,config.maxMatches()-active.get()));sample.put("cacheSizeMb",cache.stats().bytes()/1048576);sample.put("cacheHits",cache.stats().hits());sample.put("cacheMisses",cache.stats().misses());return sample;
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
                try{heartbeat.tick();if(heartbeat.epoch()!=epoch){cancelled.set(true);children.forEach(SandboxBot::close);}else renew(epoch);}
                catch(CentralClient.CentralFailure e){if(Set.of(400,401,403,404,409).contains(e.status)){cancelled.set(true);children.forEach(SandboxBot::close);}}
                catch(IOException e){/* bounded by match deadline; no new claims during a network interruption */}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
            },5,5,TimeUnit.SECONDS);
            var tasks=new ArrayList<Future<Boolean>>();
            for(var node:jobs){var job=ExecutionJob.parse(node,identity.workerId(),epoch);held.add(job.executionId());leases.put(job.executionId(),job.leaseUntil());tasks.add(pool.submit(()->execute(job,node,journal,timer)));}
            int failed=0;for(var task:tasks)try{if(!task.get())failed++;}catch(ExecutionException e){failed++;}
            log.println("Batch upload: "+(tasks.size()-failed)+" acknowledged, "+failed+" failed or pending. Unacknowledged state retained.");
            return failed==0?0:1;
        }finally{
            cancelled.set(true);children.forEach(SandboxBot::close);pool.shutdownNow();timer.shutdownNow();
            pool.awaitTermination(10,TimeUnit.SECONDS);timer.awaitTermination(5,TimeUnit.SECONDS);
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
    }
    private void renew(long epoch)throws IOException,InterruptedException {
        var ids=List.copyOf(held);if(ids.isEmpty())return;
        var response=central.post(identity,"jobs/renew",Map.of("epoch",epoch,"executions",ids));var grants=response.path("executions");
        if(!grants.isArray()||grants.size()!=ids.size())throw new IOException("Invalid lease response");var seen=new HashSet<UUID>();
        for(var node:grants){UUID execution;try{execution=UUID.fromString(node.path("executionId").asText());}catch(Exception e){throw new IOException("Invalid lease identity");}
            if(!ids.contains(execution)||!seen.add(execution))throw new IOException("Invalid lease ownership");String status=node.path("status").asText();
            if(status.equals("ACTIVE"))try{leases.put(execution,java.time.Instant.parse(node.path("leaseUntil").asText()));}catch(Exception e){throw new IOException("Invalid lease deadline");}
            if(!status.equals("ACTIVE")){if(status.equals("DONE")){held.remove(execution);continue;}lost.add(execution);held.remove(execution);processes.getOrDefault(execution,Set.of()).forEach(SandboxBot::close);}
        }
    }
    private boolean execute(ExecutionJob job,com.fasterxml.jackson.databind.JsonNode node,ExecutionJournal journal,ScheduledExecutorService timer) {
        Path directory=null;SandboxBot a=null,b=null;ScheduledFuture<?> watchdog=null;long deadline=Long.MAX_VALUE;boolean finished=false;
        active.incrementAndGet();
        try {
            var client=new ArtifactClient(central,identity,cache);
            try(var artifactA=client.acquire(job.executionId(),job.match().botA());var artifactB=client.acquire(job.executionId(),job.match().botB())) {
                if(cancelled.get()||lost.contains(job.executionId())||!java.time.Instant.now().isBefore(leases.getOrDefault(job.executionId(),job.leaseUntil())))throw new IOException("Execution unavailable");
                directory=journal.begin(job,node);
                Path stagedA=journal.stage(directory,"A",artifactA.path(),job.match().botA()),stagedB=journal.stage(directory,"B",artifactB.path(),job.match().botB());
                var started=central.post(identity,"jobs/"+job.jobId()+"/start",Map.of("executionId",job.executionId(),"epoch",job.epoch()));
                if(!started.path("executionId").asText().equals(job.executionId().toString())||!"RUNNING".equals(started.path("status").asText()))throw new IOException("Invalid start acknowledgement");
                deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(job.match().matchHardTimeoutMs());
                a=new SandboxBot(stagedA,job.match().botA(),job.executionId(),deadline);b=new SandboxBot(stagedB,job.match().botB(),job.executionId(),deadline);
                children.add(a);children.add(b);bots.addAndGet(2);var first=a;var second=b;
                processes.put(job.executionId(),Set.of(a,b));
                watchdog=timer.schedule(()->{first.close();second.close();},job.match().matchHardTimeoutMs(),TimeUnit.MILLISECONDS);
                if(cancelled.get()||lost.contains(job.executionId()))throw new IOException("Execution cancelled");
                var result=new HeadlessMatch().run(job.match(),a,b,deadline,()->{try{var running=central.post(identity,"jobs/"+job.jobId()+"/running",Map.of("executionId",job.executionId(),"epoch",job.epoch()));if(!running.path("executionId").asText().equals(job.executionId().toString()))throw new IOException("Invalid running acknowledgement");}catch(Exception unavailable){throw new IllegalStateException("Gameplay acknowledgement unavailable",unavailable);}});
                if(cancelled.get()||lost.contains(job.executionId()))throw new IOException("Execution cancelled");journal.finish(directory,job,result);finished=true;
                return new ResultUploader(central,identity,journal).upload(directory);
            }
        }catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            if(directory!=null&&!finished)try{journal.failure(directory,job,System.nanoTime()>=deadline?"MATCH_HARD_TIMEOUT":e instanceof com.rpsbattle.bot.BotInfrastructureException?"SANDBOX_UNAVAILABLE":"EXECUTION_FAILED");}catch(IOException ignored){}
            // Started or ambiguous requests retain the reservation. Never decline a possibly running match.
            if(directory==null)try{new ClaimClient(central,identity).decline(job.jobId(),job.executionId(),"ARTIFACT_UNAVAILABLE");}catch(Exception ignored){}
            return false;
        }finally{
            if(watchdog!=null)watchdog.cancel(false);
            if(a!=null){a.close();b.close();children.remove(a);children.remove(b);bots.addAndGet(-2);}active.decrementAndGet();
            if(directory!=null)try{journal.clearStaged(directory);}catch(IOException ignored){}
            held.remove(job.executionId());processes.remove(job.executionId());leases.remove(job.executionId());
        }
    }
}
