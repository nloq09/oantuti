package com.rpsbattle.operations;

import com.rpsbattle.auth.AccountPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Log only server-generated IDs, fixed route categories and validated resource IDs; no query/body. */
public class RequestLogFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);
        MDC.put("requestId",id);
        try {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            String user=auth!=null && auth.getPrincipal() instanceof AccountPrincipal p?p.id().toString():"guest";
            MDC.put("userId",user);
            String path=request.getRequestURI().substring(request.getContextPath().length());
            String resource="";String kind="other";
            var parts=path.split("/");
            if(parts.length>=3 && parts[1].equals("api") && java.util.Set.of("matches","tournaments","bots","auth","account","admin").contains(parts[2])) {
                kind=parts[2];
                if(parts.length>=4 && parts[3].matches("[A-F0-9]{8}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
                    resource=parts[3];MDC.put(switch(kind){case "matches"->"matchId";case "tournaments"->"tournamentId";default->"botId";},resource);
                }
            }
            try {chain.doFilter(request,response);}
            finally {if(!kind.equals("other"))LoggerFactory.getLogger(getClass()).info("event=http_request requestId={} userId={} route={} resourceId={} status={}",id,user,kind,resource,response.getStatus());}
        } finally {MDC.remove("requestId");MDC.remove("userId");MDC.remove("matchId");MDC.remove("tournamentId");MDC.remove("botId");}
    }
}
