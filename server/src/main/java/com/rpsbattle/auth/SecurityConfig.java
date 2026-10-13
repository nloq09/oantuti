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
            CsrfTokenRepository tokens, ObjectMapper json,AuthService authService) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable);
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true));
        http.addFilterAfter(new SessionAccountFilter(authService,contexts),SecurityContextHolderFilter.class);
        http.csrf(csrf -> csrf.csrfTokenRepository(tokens)
                // Legacy game mutations still use seat/bot capabilities. Account ownership and their
                // same-origin CSRF migration are intentionally deferred to Groups 3–6.
                .ignoringRequestMatchers(request -> {
                    var path=request.getServletPath();
                    return !path.startsWith("/api/auth/") && !path.startsWith("/api/account/") && !path.startsWith("/api/admin/");
                }));
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/account/**").authenticated().anyRequest().permitAll());
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
