package com.rpsbattle.worker.remote;

import com.rpsbattle.auth.AuthException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Remote machine identities are deliberately separate from account sessions and local match workers. */
@Service
public class WorkerIdentityService {
    private final JdbcTemplate db;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();
    public WorkerIdentityService(JdbcTemplate db, Clock clock) { this.db=db; this.clock=clock; }
    public record Token(UUID id, String registrationToken, Instant expiresAt, int remainingUses) {}
    public record TokenView(UUID id, Instant expiresAt, int remainingUses, boolean revoked) {}
    public record Registration(String registrationToken, String name, String workerVersion, Integer protocolVersion) {}
    public record Identity(UUID workerId, String name, String workerVersion, int protocolVersion, String status, String approvalStatus, String trustLevel) {}
    public record Enrolled(Identity worker, String workerCredential) {}

    public static String hash(String secret) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String secret(String prefix) { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return prefix+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static void validSecret(String value, String prefix) {
        if(value==null || !value.matches(prefix+"[A-Za-z0-9_-]{43}")) throw new AuthException("WORKER_CREDENTIAL_INVALID",401);
    }
    @Transactional
    public Token issue(UUID actor, int uses, int lifetimeMinutes) {
        if(uses<1 || uses>100 || lifetimeMinutes<1 || lifetimeMinutes>1440) throw new AuthException("INVALID_WORKER_TOKEN_POLICY",400);
        var id=UUID.randomUUID(); var raw=secret("wrt_"); var now=clock.instant(); var end=now.plusSeconds(lifetimeMinutes*60L);
        db.update("insert into worker_registration_tokens(id,token_hash,remaining_uses,expires_at,created_by,created_at) values(?,?,?,?,?,?)",id,hash(raw),uses,at(end),actor,at(now));
        event("REGISTRATION_TOKEN_CREATED",id,actor); return new Token(id,raw,end,uses);
    }
    public List<TokenView> tokens() {
        return db.query("select id,expires_at,remaining_uses,revoked_at from worker_registration_tokens order by created_at desc limit 100",(r,n)->new TokenView(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getInt(3),r.getTimestamp(4)!=null));
    }
    @Transactional
    public void revokeToken(UUID id, UUID actor) {
        if(db.update("update worker_registration_tokens set revoked_at=? where id=? and revoked_at is null",at(clock.instant()),id)==0) {
            if(db.queryForObject("select count(*) from worker_registration_tokens where id=?",Integer.class,id)==0) throw new AuthException("WORKER_TOKEN_NOT_FOUND",404);
            return;
        }
        event("REGISTRATION_TOKEN_REVOKED",id,actor);
    }
    @Transactional
    public Enrolled register(Registration body) {
        if(body==null) throw new AuthException("INVALID_WORKER_REGISTRATION",400);
        validSecret(body.registrationToken(),"wrt_");
        if(body.name()==null || !body.name().matches("[\\p{L}\\p{N} _.-]{1,80}") || body.name().isBlank() || body.workerVersion()==null || !body.workerVersion().matches("[A-Za-z0-9._+-]{1,64}")) throw new AuthException("INVALID_WORKER_REGISTRATION",400);
        if(!Integer.valueOf(1).equals(body.protocolVersion())) throw new AuthException("WORKER_PROTOCOL_INCOMPATIBLE",409);
        var now=clock.instant();
        // Atomic conditional consumption prevents concurrent reuse, including across Central instances.
        if(db.update("update worker_registration_tokens set remaining_uses=remaining_uses-1 where token_hash=? and remaining_uses>0 and revoked_at is null and expires_at>?",hash(body.registrationToken()),at(now))!=1) throw new AuthException("WORKER_REGISTRATION_TOKEN_INVALID",401);
        var id=UUID.randomUUID(); var credential=secret("wrc_");
        db.update("insert into workers(id,name,worker_version,protocol_version,status,approval_status,trust_level,registered_at) values(?,?,?,1,'STARTING','PENDING_APPROVAL','UNTRUSTED',?)",id,body.name().strip(),body.workerVersion(),at(now));
        db.update("insert into worker_credentials(worker_id,credential_hash,created_at) values(?,?,?)",id,hash(credential),at(now));
        event("WORKER_REGISTERED",id,null); return new Enrolled(identity(id),credential);
    }
    public UUID authenticate(String credential) {
        validSecret(credential,"wrc_");
        var ids=db.query("select c.worker_id from worker_credentials c join workers w on w.id=c.worker_id where c.credential_hash=? and c.revoked_at is null and w.status<>'DISABLED' and w.approval_status<>'REJECTED'",(r,n)->r.getObject(1,UUID.class),hash(credential));
        if(ids.isEmpty()) throw new AuthException("WORKER_CREDENTIAL_INVALID",401);
        return ids.getFirst();
    }
    public Identity identity(UUID id) {
        var rows=db.query("select id,name,worker_version,protocol_version,status,approval_status,trust_level from workers where id=?",(r,n)->new Identity(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getString(5),r.getString(6),r.getString(7)),id);
        if(rows.isEmpty()) throw new AuthException("WORKER_NOT_FOUND",404); return rows.getFirst();
    }
    private static java.sql.Timestamp at(Instant time) {return java.sql.Timestamp.from(time);}
    private void event(String type, UUID resource, UUID actor) { db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at) values(?,?,?,?,?)",UUID.randomUUID(),type,resource,actor,at(clock.instant())); }
}
