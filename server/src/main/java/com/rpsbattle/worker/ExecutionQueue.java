package com.rpsbattle.worker;

import com.rpsbattle.online.DemoRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

/** FIFO with atomic batch admission. Reservations include jobs not yet offered by the scheduler. */
@Component
public class ExecutionQueue {
    private final int capacity;
    private final Set<String> reservations = new HashSet<>(), offered = new HashSet<>();
    private final ArrayDeque<MatchExecutionRequest> pending = new ArrayDeque<>();
    private boolean accepting = true;
    public ExecutionQueue(@Value("${rps.worker.max-pending-matches:256}") int capacity) {
        if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("Invalid queue capacity");
        this.capacity = capacity;
    }
    public synchronized void reserve(Collection<String> ids) {
        var fresh = new HashSet<>(ids); fresh.removeAll(reservations);
        if (!accepting || reservations.size() + fresh.size() > capacity) throw new DemoRequestException("SERVER_BUSY");
        reservations.addAll(fresh);
    }
    public synchronized boolean offer(MatchExecutionRequest request) {
        if (!accepting || offered.contains(request.match().id)) return false;
        reserve(List.of(request.match().id));
        offered.add(request.match().id); pending.addLast(request); return true;
    }
    public synchronized MatchExecutionRequest poll() {
        var request = pending.pollFirst();
        if (request != null) reservations.remove(request.match().id);
        return request;
    }
    public synchronized void release(Collection<String> ids) {
        reservations.removeAll(ids); offered.removeAll(ids);
        pending.removeIf(request -> ids.contains(request.match().id));
    }
    public synchronized void drain() { accepting = false; pending.clear(); reservations.clear(); offered.clear(); }
    public synchronized boolean accepting() { return accepting; }
    public synchronized Set<String> reservedIds(){return Set.copyOf(reservations);}
    public synchronized int size() { return reservations.size(); }
    public int capacity() { return capacity; }
    public record QueueEntry(String matchId,String tournamentId,java.time.Instant scheduledAt,String status){}
    public synchronized List<QueueEntry> snapshot(){
        var entries=new ArrayList<QueueEntry>();var seen=new HashSet<String>();
        for(var request:pending){entries.add(new QueueEntry(request.match().id,request.tournament().id,request.match().scheduledAt,"QUEUED"));seen.add(request.match().id);if(entries.size()==200)return List.copyOf(entries);}
        for(var id:reservations)if(!seen.contains(id)){entries.add(new QueueEntry(id,null,null,"RESERVED"));if(entries.size()==200)break;}
        return List.copyOf(entries);
    }
}
