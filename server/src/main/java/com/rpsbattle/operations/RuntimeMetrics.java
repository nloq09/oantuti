package com.rpsbattle.operations;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import com.rpsbattle.auth.AccountPrincipal;

@Component
public class RuntimeMetrics implements HttpSessionAttributeListener, HttpSessionListener, HttpSessionIdListener {
    private final Counter loginFailures, botTimeouts;
    private final Set<String> sockets=ConcurrentHashMap.newKeySet();
    // Session IDs are used only for lifecycle bookkeeping; never exposed in metrics or logs.
    private final Map<String,UUID> users= new ConcurrentHashMap<>();
    public RuntimeMetrics(MeterRegistry meters) {
        loginFailures=meters.counter("login_failure_total");botTimeouts=meters.counter("bot_timeout_total");
        Gauge.builder("websocket_sessions",sockets,Set::size).register(meters);
        Gauge.builder("active_users",users,map->map.values().stream().distinct().count()).register(meters);
    }
    public void loginFailed(){loginFailures.increment();}
    public void botFailure(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof java.util.concurrent.TimeoutException) {botTimeouts.increment();break;}
    }
    public void socketOpened(String id){sockets.add(id);}
    public void socketClosed(String id){sockets.remove(id);}
    private void refresh(HttpSession session) {
        Object value=session.getAttribute("SPRING_SECURITY_CONTEXT");
        Authentication auth=value instanceof SecurityContext context?context.getAuthentication():null;
        if(auth!=null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal principal) users.put(session.getId(),principal.id());
        else users.remove(session.getId());
    }
    public void attributeAdded(HttpSessionBindingEvent event){if(event.getName().equals("SPRING_SECURITY_CONTEXT"))refresh(event.getSession());}
    public void attributeReplaced(HttpSessionBindingEvent event){attributeAdded(event);}
    public void attributeRemoved(HttpSessionBindingEvent event){if(event.getName().equals("SPRING_SECURITY_CONTEXT"))users.remove(event.getSession().getId());}
    public void sessionDestroyed(HttpSessionEvent event){users.remove(event.getSession().getId());}
    public void sessionIdChanged(HttpSessionEvent event,String oldId){users.remove(oldId);refresh(event.getSession());}
}
