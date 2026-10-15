package com.rpsbattle.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.account.domain.AccountStatus;
import com.rpsbattle.account.repository.UserRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService accountDetails(UserRepository users) {
        return username -> {
            var account=users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("INVALID_CREDENTIALS"));
            return org.springframework.security.core.userdetails.User.withUsername(account.username())
                    .password(account.passwordHash()).roles(account.role().name())
                    .disabled(account.status()!=AccountStatus.ACTIVE).build();
        };
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContexts() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrfTokens)));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository tokens, ObjectMapper json,AuthService authService,com.rpsbattle.operations.RequestLimits limits,com.rpsbattle.operations.RuntimeMetrics metrics,com.rpsbattle.admin.AdminOperationJournal journal) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable);
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true));
        http.addFilterAfter(new SessionAccountFilter(authService,contexts),SecurityContextHolderFilter.class);
        http.addFilterBefore(new com.rpsbattle.admin.AdminOperationAuditFilter(journal,authService),org.springframework.security.web.csrf.CsrfFilter.class);
        http.addFilterBefore(new com.rpsbattle.operations.RequestLogFilter(),org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.addFilterAfter(new com.rpsbattle.operations.RequestLimitFilter(limits,metrics,json),com.rpsbattle.operations.RequestLogFilter.class);
        http.csrf(csrf -> csrf.csrfTokenRepository(tokens)
                // Guest seat mutations remain capabilities. Authenticated game/tournament requests
                // can exercise account-owned bots and therefore require CSRF as well.
                .ignoringRequestMatchers(request -> {
                    var path=request.getServletPath();
                    if(path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
                    if(path.startsWith("/api/tournaments") || path.equals("/api/bots") || path.startsWith("/api/auth/") || path.startsWith("/api/account/") || path.startsWith("/api/admin/")) return false;
                    var session=request.getSession(false);
                    return !((path.startsWith("/api/matches") || path.startsWith("/api/tournaments")) && session!=null && session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)!=null);
                }));
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health","/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**","/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/account/**","/api/bots").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/tournaments","/api/tournaments/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/tournaments/**").authenticated().anyRequest().permitAll());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,error)-> {
                    response.setStatus(401);response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(),Map.of("error","UNAUTHENTICATED"));
                })
                .accessDeniedHandler((request,response,error)-> {
                    response.setStatus(403);response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(),Map.of("error",error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN"));
                }));
        return http.build();
    }
}
