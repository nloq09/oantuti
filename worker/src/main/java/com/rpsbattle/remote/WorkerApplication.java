package com.rpsbattle.remote;

public final class WorkerApplication {
    public static void main(String[] args) {
        int result=run(args,System.getenv(),System.out);
        if(result!=0)System.exit(result);
    }
    static int run(String[] args,java.util.Map<String,String> env,java.io.PrintStream out) {
        if(args.length==1 && args[0].equals("--help")) {
            out.println("RPS Worker 0.1.0 (registration foundation)\nJava 21+. Set WORKER_REGISTRATION_TOKEN for first enrollment.\njava -jar rps-worker.jar --central=https://central.example --name=LAB-PC-01 --state-dir=./worker-state\nLAN/local only: add --allow-insecure-http. Keep worker-state private and persistent.");return 0;
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
            out.println("Registration foundation only: heartbeat, approval UI and job execution are not enabled yet.");
            return 0;
        } catch(InterruptedException e){Thread.currentThread().interrupt();out.println("Worker interrupted; persisted identity retained.");return 1;}
        catch(Exception e){out.println("Worker connection or private identity storage failed. Check Central, credentials and state directory. If initial enrollment response was lost, ask admin for a new token; do not retry enrollment automatically.");return 1;}
    }
}
