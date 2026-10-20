package com.rpsbattle.remote;

public final class WorkerApplication {
    public static void main(String[] args) {
        int result=run(args,System.getenv(),System.out);
        if(result!=0)System.exit(result);
    }
    static int run(String[] args,java.util.Map<String,String> env,java.io.PrintStream out) {
        if(args.length>0&&args[0].equals("benchmark")){
            try{var options=new java.util.ArrayList<String>();options.add("--central=https://benchmark.invalid");for(int i=1;i<args.length;i++){if(!args[i].startsWith("--state-dir="))throw new IllegalArgumentException();options.add(args[i]);}new LocalBenchmark().run(WorkerConfig.parse(options.toArray(String[]::new),java.util.Map.of()),out);return 0;}
            catch(InterruptedException e){Thread.currentThread().interrupt();out.println("Benchmark interrupted; sandbox cleanup requested.");return 1;}
            catch(Exception e){out.println("Benchmark failed. Stop the agent using this state; check Docker Linux and python:3.12-slim. No credentials were sent.");return 1;}
        }
        if(args.length==1 && args[0].equals("--help")) {
            out.println("RPS Worker 0.11.0\nLocal baseline: java -jar rps-worker.jar benchmark --state-dir=./benchmark-state\nJava 21+. Set WORKER_REGISTRATION_TOKEN for first enrollment.\njava -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --state-dir=./worker-state --max-matches=1 --max-bots=2\nLAN/local only: add --allow-insecure-http. Use --once for one heartbeat, --identity-only to check credentials, --prefetch for assigned artifacts only. --serve continuously claims compatible jobs; --run-once executes/uploads one batch; --upload-pending retries completed results; --recover reconciles interrupted state without replaying bots. Cache: --cache-dir=PATH --cache-mb=20480. Keep worker-state private and persistent.");return 0;
        }
        WorkerConfig config;
        try{config=WorkerConfig.parse(args,env);}catch(RuntimeException e){out.println("Invalid configuration. Use --help; HTTPS is required except explicit LAN/local HTTP.");return 2;}
        try(var store=new IdentityStore(config.stateDirectory());var central=new CentralClient(config.central())) {
            var identity=store.load();
            if(identity==null) {
                identity=central.register(config.registrationToken(),config.name());
                store.save(identity);
                out.println("Worker identity saved privately. Registration token is not persisted.");
            }
            var current=central.identity(identity);
            out.println("Worker "+identity.workerId()+" connected. Approval: "+current.path("approvalStatus").asText()+". Status: "+current.path("status").asText());
            if(config.identityOnly())return 0;
            if(config.uploadPending()||config.recover()){var journal=new ExecutionJournal(config.stateDirectory().resolve("executions"));int count=new RecoveryAgent(central,identity,journal).reconcile(config.recover());out.println("Recovery actions completed: "+count);return 0;}
            try(var cache=new ArtifactCache(config.cacheDirectory(),config.cacheBytes())) {
                if(config.prefetch()){int count=new ArtifactClient(central,identity,cache).prefetchAssigned();out.println("Assigned artifacts prepared: "+count+"; cache hits="+cache.stats().hits()+", misses="+cache.stats().misses());return 0;}
                var probe=new RuntimeProbe(config);
                if(config.serve())return new WorkerServe().run(central,identity,config,cache,probe,out);
                if(config.runOnce())return new ExecutionBatch(central,identity,config,cache,probe,out).run();
                var recovery=new RecoveryAgent(central,identity,new ExecutionJournal(config.stateDirectory().resolve("executions")));
                java.util.concurrent.ScheduledExecutorService retries=null;
                if(!config.once()){
                    try{int count=recovery.reconcile(true);if(count>0)out.println("Recovered persisted execution state: "+count);}catch(java.io.IOException e){out.println("Recovery pending; state retained for reconnect.");}
                    retries=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"worker-pending-recovery");thread.setDaemon(true);return thread;});
                    retries.scheduleWithFixedDelay(()->{try{int count=recovery.reconcile(true);if(count>0)out.println("Recovered persisted execution state: "+count);}catch(java.io.IOException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}},10,10,java.util.concurrent.TimeUnit.SECONDS);
                }
                out.println("Heartbeat-only mode. Use --serve to claim configured remote tournaments. Ctrl+C to stop.");
                try{return new HeartbeatAgent(central,identity,probe.capabilities(),()->{var sample=new java.util.HashMap<>(probe.heartbeat());sample.put("cacheSizeMb",cache.stats().bytes()/1048576);sample.put("cacheHits",cache.stats().hits());sample.put("cacheMisses",cache.stats().misses());return sample;},out).run(config.once());}finally{if(retries!=null){retries.shutdownNow();retries.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS);}}
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();out.println("Worker interrupted; persisted identity retained.");return 1;}
        catch(Exception e){out.println("Worker connection or private identity storage failed. Check Central, credentials and state directory. If initial enrollment response was lost, ask admin for a new token; do not retry enrollment automatically.");return 1;}
    }
}
