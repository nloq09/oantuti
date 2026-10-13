package com.rpsbattle.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AccountPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

public class RequestLimitFilter extends OncePerRequestFilter {
    private final RequestLimits limits;
    private final RuntimeMetrics metrics;
    private final ObjectMapper json;
    public RequestLimitFilter(RequestLimits limits,RuntimeMetrics metrics,ObjectMapper json){this.limits=limits;this.metrics=metrics;this.json=json;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getMethod().equals("POST")) {chain.doFilter(request,response);return;}
        String path=request.getRequestURI().substring(request.getContextPath().length());
        int quota=0;String route="";
        switch(path) {
            case "/api/auth/login" -> {route="login";quota=30;}
            case "/api/auth/register" -> {route="register";quota=10;}
            case "/api/bots" -> {route="upload";quota=20;}
            case "/api/matches" -> {route="create-match";quota=60;}
            case "/api/tournaments" -> {route="create-tournament";quota=20;}
            default -> {if(path.matches("/api/matches/[^/]+/join")){route="join";quota=120;}}
        }
        if(quota==0){chain.doFilter(request,response);return;}
        String ip=request.getRemoteAddr();var auth=SecurityContextHolder.getContext().getAuthentication();
        String actor=auth!=null && auth.getPrincipal() instanceof AccountPrincipal principal?principal.id().toString():ip;
        boolean login=route.equals("login");
        var decision=login?limits.available("login-failed:"+ip,10,900):new RequestLimits.Decision(true,0);
        if(decision.allowed()) decision=limits.take("http-ip:"+route+":"+ip,quota*2,60);
        if(decision.allowed()) decision=limits.take("http:"+route+":"+actor,quota,60);
        if(!decision.allowed()) {
            response.setStatus(429);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
            response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
            json.writeValue(response.getOutputStream(),Map.of("error","RATE_LIMITED","retryAfterSeconds",decision.retryAfterSeconds()));return;
        }
        chain.doFilter(request,response);
        if(login && response.getStatus()==401) {limits.take("login-failed:"+ip,10,900);metrics.loginFailed();}
    }
}
