package com.rpsbattle.history.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Single-server writers share a lock until commit, including first inserts with no row to lock yet. */
final class MatchWriteLocks {
    private final ConcurrentHashMap<String,Entry> entries=new ConcurrentHashMap<>();
    private static final class Entry {final ReentrantLock lock=new ReentrantLock();int references;}
    AutoCloseable acquire(String id) {
        var entry=entries.compute(id,(key,current)->{if(current==null)current=new Entry();current.references++;return current;});
        entry.lock.lock();
        Runnable release=()->{entry.lock.unlock();entries.compute(id,(key,current)->{if(current!=entry)throw new IllegalStateException("Match lock identity changed");return --current.references==0?null:current;});};
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCompletion(int status){release.run();}
            });
            return ()->{};
        }
        return release::run;
    }
}
