package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.operations.RequestLimits;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class WorkerSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain workerSecurity(HttpSecurity http, WorkerIdentityService identities, ObjectMapper json, RequestLimits limits) throws Exception {
        http.securityMatcher("/internal/**").csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(s->s.securityContextRepository(new NullSecurityContextRepository()))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/internal/workers/register").permitAll()
                .requestMatchers(HttpMethod.GET,"/internal/workers/*/identity").hasAuthority("REMOTE_WORKER")
                .requestMatchers(HttpMethod.POST,"/internal/workers/*/capabilities","/internal/workers/*/heartbeat","/internal/workers/*/jobs/claim","/internal/workers/*/jobs/*/decline","/internal/workers/*/jobs/*/start","/internal/workers/*/jobs/*/complete").hasAuthority("REMOTE_WORKER")
                .requestMatchers(HttpMethod.GET,"/internal/workers/*/jobs/assigned").hasAuthority("REMOTE_WORKER")
                .requestMatchers(HttpMethod.GET,"/internal/workers/*/artifacts/*").hasAuthority("REMOTE_WORKER")
                .anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->error(r,json,401,"WORKER_CREDENTIAL_REQUIRED"))
                .accessDeniedHandler((q,r,x)->error(r,json,403,"WORKER_FORBIDDEN")));
        http.addFilterAfter(new OncePerRequestFilter() {
            protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain) throws IOException,ServletException {
                r.setHeader("Cache-Control","no-store");
                String path=q.getRequestURI().substring(q.getContextPath().length());
                if(path.equals("/internal/workers/register") && q.getMethod().equals("POST")) {
                    var decision=limits.take("worker-register:"+q.getRemoteAddr(),10,60);
                    if(!decision.allowed()){r.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));error(r,json,429,"RATE_LIMITED");return;}
                } else {
                    var header=q.getHeader("Authorization");
                    if(header!=null) {
                        try {
                            if(!header.startsWith("Bearer ")) throw new AuthException("WORKER_CREDENTIAL_INVALID",401);
                            UUID id=identities.authenticate(header.substring(7));
                            if(q.getMethod().equals("POST")&&path.endsWith("/jobs/claim")){var decision=limits.take("worker-claim:"+id,120,60);if(!decision.allowed()){r.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));error(r,json,429,"RATE_LIMITED");return;}}
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("REMOTE_WORKER"))));
                        } catch(AuthException e) {error(r,json,e.status(),e.code());return;}
                        catch(org.springframework.dao.DataAccessException e){error(r,json,503,"DATABASE_UNAVAILABLE");return;}
                    }
                }
                chain.doFilter(q,r);
            }
        },SecurityContextHolderFilter.class);
        return http.build();
    }
    private static void error(HttpServletResponse r,ObjectMapper json,int status,String code)throws IOException {r.setStatus(status);r.setContentType("application/json");r.setHeader("Cache-Control","no-store");json.writeValue(r.getOutputStream(),Map.of("error",code));}
}
