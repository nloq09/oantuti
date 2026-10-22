package com.rpsbattle.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Redact only the read model; original audit records remain intact. */
@Component
public class AuditRedactor {
    private final ObjectMapper json;
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|passwd|secret|credential|authorization|cookie|session|token|privatekey|apikey|sourcecode|databaseurl|jdbcurl).*");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)([\\w.-]*(?:password|passwd|secret|credential|authorization|cookie|session|token|private.?key|api.?key|database.?url|jdbc.?url)[\\w.-]*[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^,;\\s}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern URI_PASSWORD = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://[^\\s/:@]+:)[^\\s@]+@");
    private static final Pattern WORKER_SECRET = Pattern.compile("\\b(?:wrt_|wrc_)[A-Za-z0-9_-]{16,}");
    public AuditRedactor(ObjectMapper json) { this.json=json; }
    public Object redact(Object value) {
        if (value==null) return null;
        if (value instanceof String s) {
            try { Object parsed=json.readValue(s,Object.class); if(parsed instanceof Map || parsed instanceof List) return json.writeValueAsString(walk(parsed)); }
            catch (Exception ignored) { /* Legacy values also contain enum and Java record text. */ }
            return text(s);
        }
        return walk(value);
    }
    private Object walk(Object value) {
        if(value instanceof Map<?,?> map){var safe=new LinkedHashMap<String,Object>();map.forEach((key,item)->{String name=key.toString();safe.put(name,SECRET_KEY.matcher(name.replaceAll("[^a-zA-Z]", "")).matches()?"[REDACTED]":walk(item));});return safe;}
        if(value instanceof List<?> list) return list.stream().map(this::walk).toList();
        return value instanceof String s?redact(s):value;
    }
    private String text(String s) { return WORKER_SECRET.matcher(URI_PASSWORD.matcher(ASSIGNMENT.matcher(BEARER.matcher(s).replaceAll("Bearer [REDACTED]")).replaceAll("$1[REDACTED]")).replaceAll("$1[REDACTED]@")).replaceAll("[REDACTED]"); }
}
