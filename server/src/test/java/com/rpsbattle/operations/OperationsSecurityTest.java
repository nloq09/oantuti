package com.rpsbattle.operations;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import com.rpsbattle.online.MatchRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="rps.rate-limit.multiplier=1")
@AutoConfigureMockMvc
class OperationsSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired MeterRegistry meters;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired MatchRegistry rooms;
    @Test void tenFailedLoginsBlockBruteForceDespiteSpoofedForwardedHeaders() throws Exception {
        double before=meters.counter("login_failure_total").count();
        for(int i=0;i<10;i++)mvc.perform(post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.10");return r;})
                .header("X-Forwarded-For","198.51.100."+i).contentType("application/json").content("{\"username\":\"missing\",\"password\":\"short\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.10");return r;})
                .contentType("application/json").content("{\"username\":\"missing\",\"password\":\"short\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
        assertThat(meters.counter("login_failure_total").count()-before).isEqualTo(10);
    }
    @Test void createMatchLimitRejectsBeforeMutationAndCsrfRemainsRequired() throws Exception {
        int before=rooms.getActiveMatches().size();
        for(int i=0;i<60;i++)mvc.perform(post("/api/matches").with(r->{r.setRemoteAddr("192.0.2.20");return r;})
                .contentType("application/json").content("{\"mode\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/matches").with(r->{r.setRemoteAddr("192.0.2.20");return r;})
                .contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("RATE_LIMITED"));
        assertThat(rooms.getActiveMatches()).hasSize(before);
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF_INVALID"));
    }
    @Test void onlyAdminCanReadMetricsAndSensitiveActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        var account=registration.register("metrics_user","metrics@example.com","Metrics Password 123","Metrics");
        mvc.perform(get("/actuator/metrics").with(authentication(AuthService.sessionAuthentication(account)))).andExpect(status().isForbidden());
        var admin=users.save(new User(account.id(),account.username(),account.email(),account.passwordHash(),account.displayName(),UserRole.ADMIN,account.status(),account.createdAt(),account.updatedAt(),account.lastLoginAt(),account.authVersion()));
        mvc.perform(get("/actuator/metrics").with(authentication(AuthService.sessionAuthentication(admin)))).andExpect(status().isOk());
        for(String name:java.util.List.of("active_users","active_matches","running_tournaments","uploaded_bots","bot_timeout_total","login_failure_total","websocket_sessions","worker_pending_matches"))
            mvc.perform(get("/actuator/metrics/"+name).with(authentication(AuthService.sessionAuthentication(admin)))).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(authentication(AuthService.sessionAuthentication(admin)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/system").with(authentication(AuthService.sessionAuthentication(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.workers.maxPendingMatches").value(256));
    }
}
