package com.rpsbattle.operations;

import com.rpsbattle.bot.ownership.BotRepository;
import com.rpsbattle.online.MatchRegistry;
import com.rpsbattle.tournament.registry.TournamentRegistry;
import com.rpsbattle.tournament.domain.TournamentStatus;
import com.rpsbattle.worker.WorkerManager;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;

@Configuration
public class OperationsConfig {
    @Bean ServletListenerRegistrationBean<RuntimeMetrics> sessionMetrics(RuntimeMetrics metrics){return new ServletListenerRegistrationBean<>(metrics);}
    @Bean MeterBinder gameMetrics(org.springframework.beans.factory.ObjectProvider<MatchRegistry> rooms,
            org.springframework.beans.factory.ObjectProvider<TournamentRegistry> tournaments,
            org.springframework.beans.factory.ObjectProvider<BotRepository> bots,
            org.springframework.beans.factory.ObjectProvider<WorkerManager> workers) {
        return registry -> {
            Gauge.builder("active_matches",rooms,r->r.getObject().getActiveMatches().stream().filter(m->{synchronized(m){return !m.state().isFinished() && m.infrastructureError()==null;}}).count()).register(registry);
            Gauge.builder("running_tournaments",tournaments,r->r.getObject().findAll().stream().filter(t->{synchronized(t){return t.status==TournamentStatus.RUNNING;}}).count()).register(registry);
            Gauge.builder("uploaded_bots",bots,r->{try{return r.getObject().countActive();}catch(RuntimeException unavailable){return Double.NaN;}}).register(registry);
            Gauge.builder("worker_active_matches",workers,r->r.getObject().active()).register(registry);
            Gauge.builder("worker_pending_matches",workers,r->r.getObject().pending()).register(registry);
        };
    }
}
