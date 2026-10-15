package com.rpsbattle.admin;
import com.rpsbattle.account.domain.User;
import com.rpsbattle.auth.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
/** Record rejected requests without reading payloads, query strings, passwords or tokens. */
public class AdminOperationAuditFilter extends OncePerRequestFilter {
    private final AdminOperationJournal journal;private final AuthService auth;
    public AdminOperationAuditFilter(AdminOperationJournal journal,AuthService auth){this.journal=journal;this.auth=auth;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI().substring(request.getContextPath().length());boolean admin=path.startsWith("/api/admin/");
        if(!admin){chain.doFilter(request,response);return;}
        User actor=null;try{actor=auth.current(SecurityContextHolder.getContext().getAuthentication()).orElse(null);}catch(RuntimeException unavailable){}
        try{chain.doFilter(request,response);}finally{if(response.getStatus()>=400){String[] parts=path.split("/");String resource=parts.length>3?parts[3].toUpperCase(Locale.ROOT):"OTHER";
            if(!Set.of("USERS","BOTS","MATCHES","TOURNAMENTS","DISPUTES","REPLAYS","SYSTEM","AUDIT","DASHBOARD").contains(resource))resource="OTHER";
            resource=switch(resource){case "USERS"->"USER";case "BOTS"->"BOT";case "MATCHES"->"MATCH";case "TOURNAMENTS"->"TOURNAMENT";case "DISPUTES"->"DISPUTE";case "REPLAYS"->"REPLAY";default->resource;};
            String target=parts.length>4&&parts[4].matches("[a-fA-F0-9-]{8,64}")?parts[4]:null;
            journal.record(actor,resource,target,request.getMethod()+"_REQUEST","FAILURE",response.getStatus(),"HTTP_"+response.getStatus());}}
    }
}
