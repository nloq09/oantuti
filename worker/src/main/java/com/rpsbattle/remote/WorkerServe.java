package com.rpsbattle.remote;
import java.io.*;

/** Opt-in continuous pull, reusing the exact bounded batch and durable recovery path. */
public final class WorkerServe {
    public int run(CentralClient central,IdentityStore.Identity identity,WorkerConfig config,ArtifactCache cache,RuntimeProbe probe,PrintStream log)throws InterruptedException {
        long delay=5000;boolean waiting=false;
        while(!Thread.currentThread().isInterrupted()){
            try{
                central.identity(identity);
                new ExecutionBatch(central,identity,config,cache,probe,log).run();
                delay=5000;waiting=false;
            }catch(CentralClient.CentralFailure e){
                if(e.status==401||e.status==404){log.println("Worker credential rejected. Continuous execution stopped; private state retained.");return 1;}
                if(!waiting)log.println("Waiting for approval, compatible capacity or Central recovery.");waiting=true;delay=Math.min(30000,delay*2);
            }catch(IOException e){if(!waiting)log.println("Central/execution state pending; retrying with the same identity and journal.");waiting=true;delay=Math.min(30000,delay*2);}
            Thread.sleep(delay);
        }
        return 0;
    }
}
