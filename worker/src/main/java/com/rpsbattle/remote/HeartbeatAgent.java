package com.rpsbattle.remote;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;

public class HeartbeatAgent {
    private final CentralClient central;
    private final IdentityStore.Identity identity;
    private final Map<String,Object> capabilities;
    private final Supplier<Map<String,Object>> sample;
    private final PrintStream log;
    private long epoch,sequence;
    private String lastStatus="";
    public HeartbeatAgent(CentralClient central,IdentityStore.Identity identity,Map<String,Object> capabilities,Supplier<Map<String,Object>> sample,PrintStream log){this.central=central;this.identity=identity;this.capabilities=capabilities;this.sample=sample;this.log=log;}
    public void tick()throws IOException,InterruptedException {
        try {
            if(epoch==0){var session=central.post(identity,"capabilities",capabilities);epoch=session.path("epoch").asLong();if(epoch<1)throw new IOException("Invalid heartbeat session");sequence=0;}
            var body=new HashMap<>(sample.get());body.put("epoch",epoch);body.put("sequence",++sequence);
            var response=central.post(identity,"heartbeat",body);String status=response.path("status").asText()+" / "+response.path("approvalStatus").asText();
            if(!status.equals(lastStatus)){log.println("Heartbeat: "+status);lastStatus=status;}
        }catch(CentralClient.CentralFailure failure){if(failure.status==409)epoch=0;throw failure;}
    }
    public int run(boolean once)throws InterruptedException {
        boolean disconnected=false;long delay=5000;
        while(!Thread.currentThread().isInterrupted()) {
            try{tick();if(disconnected)log.println("Central connection restored.");disconnected=false;delay=5000;if(once)return 0;}
            catch(CentralClient.CentralFailure failure){if(Set.of(400,401,403,404).contains(failure.status)){log.println("Central rejected worker authentication/configuration. Agent stopped.");return 1;}if(!disconnected)log.println("Central unavailable; retrying heartbeat with existing identity.");disconnected=true;if(once)return 1;delay=Math.min(30000,delay*2);}
            catch(IOException failure){if(!disconnected)log.println("Central unavailable; retrying heartbeat with existing identity.");disconnected=true;if(once)return 1;delay=Math.min(30000,delay*2);}
            Thread.sleep(delay);
        }
        return 0;
    }
}
