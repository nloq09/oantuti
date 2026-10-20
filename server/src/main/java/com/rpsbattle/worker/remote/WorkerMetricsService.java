package com.rpsbattle.worker.remote;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Bounded recent samples; these are control-plane metrics, never a hardware capacity promise. */
@Service
public class WorkerMetricsService {
    private final JdbcTemplate db;private final Clock clock;
    public WorkerMetricsService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    public record Metrics(Instant measuredAt,long totalWorkers,long onlineWorkers,long activeSlots,long queuedJobs,long leasedJobs,long acceptedResults,long receiptsLast60Seconds,double receiptsPerSecond60s,long expiredAttempts,long totalAttempts,double infrastructureExpiryRate,long cacheHits,long cacheMisses,Double cacheHitRatio,int waitSampleCount,double queueWaitP50Ms,double queueWaitP95Ms,double queueWaitP99Ms){}
    private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
    public static double percentile(List<Long> values,double percentile){if(values.isEmpty())return 0;var sorted=new ArrayList<>(values);sorted.sort(Long::compare);return sorted.get(Math.max(0,(int)Math.ceil(percentile*sorted.size())-1));}
    public Metrics read(UUID tournament){
        Instant now=clock.instant();String scope=tournament==null?"":" and tournament_id=?";Object[] args=tournament==null?new Object[0]:new Object[]{tournament};
        String sampleScope=tournament==null?"":" where j.tournament_id=?";
        var waits=db.query("select a.claimed_at,j.created_at from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id"+sampleScope+" order by a.claimed_at desc,a.id desc limit 1024",(r,n)->Math.max(0,Duration.between(r.getTimestamp(2).toInstant(),r.getTimestamp(1).toInstant()).toMillis()),args);
        long hits=count("select coalesce(sum(cache_hits),0) from worker_heartbeats"),misses=count("select coalesce(sum(cache_misses),0) from worker_heartbeats");
        var recent=new ArrayList<Object>();recent.add(java.sql.Timestamp.from(now.minusSeconds(60)));if(tournament!=null)recent.add(tournament);
        long completed=count("select count(*) from worker_result_receipts where received_at>=?"+scope,recent.toArray());
        long attempts=count("select count(*) from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where 1=1"+(tournament==null?"":" and j.tournament_id=?"),args),expired=count("select count(*) from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id where a.status='EXPIRED'"+(tournament==null?"":" and j.tournament_id=?"),args);
        return new Metrics(now,count("select count(*) from workers"),count("select count(*) from workers where approval_status='APPROVED' and status in ('ONLINE','BUSY') and last_heartbeat_at>?",java.sql.Timestamp.from(now.minusSeconds(30))),count("select count(*) from match_execution_attempts where active_marker=1"),count("select count(*) from match_execution_jobs where status='QUEUED'"+scope,args),count("select count(*) from match_execution_jobs where status='LEASED'"+scope,args),count("select count(*) from worker_result_receipts where 1=1"+scope,args),completed,completed/60.0,expired,attempts,attempts==0?0:(double)expired/attempts,hits,misses,hits+misses==0?null:(double)hits/(hits+misses),waits.size(),percentile(waits,.5),percentile(waits,.95),percentile(waits,.99));
    }
}
