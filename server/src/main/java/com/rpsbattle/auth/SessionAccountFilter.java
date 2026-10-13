package com.rpsbattle.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Revalidate account status, credential version and role on every authenticated HTTP request. */
public class SessionAccountFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityContextRepository contexts;
    public SessionAccountFilter(AuthService auth,SecurityContextRepository contexts) { this.auth=auth;this.contexts=contexts; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof AccountPrincipal) {
            try {
                var current=auth.current(authentication);
                if(current.isEmpty()) {
                    var logout=new SecurityContextLogoutHandler();logout.setSecurityContextRepository(contexts);logout.logout(request,response,authentication);
                } else {
                    var refreshed=AuthService.sessionAuthentication(current.get());
                    if(!authentication.getAuthorities().equals(refreshed.getAuthorities())) {
                        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(refreshed);
                        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
                    }
                }
            } catch(DataAccessException error) {
                response.setStatus(503);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
                response.getWriter().write("{\"error\":\"DATABASE_UNAVAILABLE\"}");return;
            }
        }
        chain.doFilter(request,response);
    }
}
