package com.rpsbattle.remote;

public final class WorkerApplication {
    public static void main(String[] args) {
        int result=run(args,System.getenv(),System.out);
        if(result!=0)System.exit(result);
    }
    static int run(String[] args,java.util.Map<String,String> env,java.io.PrintStream out) {
        if(args.length==1 && args[0].equals("--help")) {
            out.println("RPS Worker 0.6.0\nJava 21+. Set WORKER_REGISTRATION_TOKEN for first enrollment.\njava -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --state-dir=./worker-state --max-matches=1 --max-bots=2\nLAN/local only: add --allow-insecure-http. Use --once for one heartbeat, --identity-only to check credentials, --prefetch for assigned artifacts only. --run-once executes/uploads one batch; --upload-pending retries only completed results. Cache: --cache-dir=PATH --cache-mb=20480. Keep worker-state private and persistent.");return 0;
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
            if(config.uploadPending()){var journal=new ExecutionJournal(config.stateDirectory().resolve("executions"));int count=new ResultUploader(central,identity,journal).pending();out.println("Result uploads acknowledged: "+count);return 0;}
            try(var cache=new ArtifactCache(config.cacheDirectory(),config.cacheBytes())) {
                if(config.prefetch()){int count=new ArtifactClient(central,identity,cache).prefetchAssigned();out.println("Assigned artifacts prepared: "+count+"; cache hits="+cache.stats().hits()+", misses="+cache.stats().misses());return 0;}
                var probe=new RuntimeProbe(config);
                if(config.runOnce())return new ExecutionBatch(central,identity,config,cache,probe,out).run();
                out.println("Heartbeat agent running. Automatic job claim/execution are not enabled yet. Ctrl+C to stop.");
                return new HeartbeatAgent(central,identity,probe.capabilities(),()->{var sample=new java.util.HashMap<>(probe.heartbeat());sample.put("cacheSizeMb",cache.stats().bytes()/1048576);return sample;},out).run(config.once());
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();out.println("Worker interrupted; persisted identity retained.");return 1;}
        catch(Exception e){out.println("Worker connection or private identity storage failed. Check Central, credentials and state directory. If initial enrollment response was lost, ask admin for a new token; do not retry enrollment automatically.");return 1;}
    }
}
