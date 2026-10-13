package com.rpsbattle.operations;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class RequestLimitsTest {
    static class Time extends Clock {
        Instant now=Instant.EPOCH;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void windowExpiryAndRetryDoNotExtendOnRejectedRequests() {
        var clock=new Time();var limits=new RequestLimits(clock,10,1);
        assertThat(limits.take("login",2,60).allowed()).isTrue();
        assertThat(limits.take("login",2,60).allowed()).isTrue();
        assertThat(limits.take("login",2,60)).isEqualTo(new RequestLimits.Decision(false,60));
        clock.now=Instant.ofEpochSecond(59);
        assertThat(limits.take("login",2,60).retryAfterSeconds()).isEqualTo(1);
        clock.now=Instant.ofEpochSecond(60);
        assertThat(limits.take("login",2,60).allowed()).isTrue();
    }
    @Test void saturationRejectsNewIdentitiesWithoutEvictingExistingLimits() {
        var clock=new Time();var limits=new RequestLimits(clock,1,1);
        limits.take("first",1,60);
        assertThat(limits.take("second",1,60).allowed()).isFalse();
        assertThat(limits.take("first",1,60).allowed()).isFalse();
        clock.now=Instant.ofEpochSecond(61);
        assertThat(limits.take("second",1,60).allowed()).isTrue();
    }
    @Test void checksDoNotConsumeFailureBudgetAndConcurrentRequestsCannotBypassLimit() throws Exception {
        var limits=new RequestLimits(Clock.systemUTC(),100,1);
        assertThat(limits.available("failures",3,60).allowed()).isTrue();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<100;i++)futures.add(pool.submit(()->limits.take("failures",3,60).allowed()));
            int accepted=0;for(var future:futures)if(future.get())accepted++;
            assertThat(accepted).isEqualTo(3);
        }
    }
}
