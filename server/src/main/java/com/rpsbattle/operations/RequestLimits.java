package com.rpsbattle.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

/** Fixed windows, bounded key storage, no client-provided forwarding headers. Single-instance only. */
@Component
public class RequestLimits {
    public record Decision(boolean allowed, long retryAfterSeconds) {}
    private record Window(long expires, int used) {}
    private final Clock clock;
    private final int maxKeys;
    private final double multiplier;
    private final Map<String,Window> windows = new HashMap<>();
    public RequestLimits(Clock clock, @Value("${rps.rate-limit.max-keys:10000}") int maxKeys,
            @Value("${rps.rate-limit.multiplier:1}") double multiplier) {
        if(maxKeys<1 || !Double.isFinite(multiplier) || multiplier<=0 || multiplier>100) throw new IllegalArgumentException("Invalid rate limit configuration");
        this.clock=clock;this.maxKeys=maxKeys;this.multiplier=multiplier;
    }
    public synchronized Decision take(String key, int limit, int seconds) { return decide(key,limit,seconds,true); }
    public synchronized Decision available(String key, int limit, int seconds) { return decide(key,limit,seconds,false); }
    private Decision decide(String key, int limit, int seconds, boolean consume) {
        long now=clock.millis();var w=windows.get(key);
        if(w!=null && w.expires<=now) {windows.remove(key);w=null;}
        if(w==null) {
            if(!consume)return new Decision(true,0);
            if(windows.size()>=maxKeys) windows.entrySet().removeIf(e->e.getValue().expires<=now);
            // Reject new keys while saturated; eviction of live keys would allow bypassing limits.
            if(windows.size()>=maxKeys)return new Decision(false,seconds);
            w=new Window(now+seconds*1000L,0);
        }
        if(w.used>=Math.ceil(limit*multiplier))return new Decision(false,Math.max(1,(w.expires-now+999)/1000));
        if(consume)windows.put(key,new Window(w.expires,w.used+1));
        return new Decision(true,0);
    }
}
