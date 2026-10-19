package com.rpsbattle.worker.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.upload.BotLanguage;
import com.rpsbattle.tournament.coding.WorkspaceSandbox;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoteArtifactService {
    private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;private final Path root;
    public RemoteArtifactService(JdbcTemplate db,ObjectMapper json,Clock clock,@Value("${rps.bots.directory:data/bots}")String directory){this.db=db;this.json=json;this.clock=clock;this.root=Path.of(directory).toAbsolutePath().normalize();}
    public record Download(byte[] bytes,String hash,String language,String format) {}
    @Transactional(readOnly=true)
    public Download download(UUID worker,UUID execution,UUID artifact) {
        // Authorization precedes registry/file access; arbitrary artifact enumeration returns nothing.
        var rows=db.queryForList("select a.lease_until,j.spec_json,j.spec_hash,w.trust_level from match_execution_attempts a join match_execution_jobs j on j.id=a.job_id join workers w on w.id=a.worker_id where a.id=? and a.worker_id=? and a.active_marker=1 and a.status in ('CLAIMED','RUNNING') and j.status='LEASED' and w.approval_status='APPROVED' and w.status<>'DISABLED'",execution,worker);
        if(rows.isEmpty())throw new AuthException("ARTIFACT_NOT_ASSIGNED",404);var row=rows.getFirst();
        Object expiry=row.get("lease_until");Instant lease=expiry instanceof java.sql.Timestamp t?t.toInstant():((java.time.OffsetDateTime)expiry).toInstant();
        if(!clock.instant().isBefore(lease))throw new AuthException("EXECUTION_LEASE_EXPIRED",409);
        String raw=row.get("spec_json").toString();if(!WorkerIdentityService.hash(raw).equals(row.get("spec_hash")))throw new AuthException("REMOTE_JOB_CORRUPT",409);
        RemoteMatchQueue.Spec spec;try{spec=json.readValue(raw,RemoteMatchQueue.Spec.class);}catch(Exception e){throw new AuthException("REMOTE_JOB_CORRUPT",409);}
        if(!Set.of("TRUSTED","COMMUNITY").contains(row.get("trust_level"))||spec.minimumTrust().equals("TRUSTED")&&!row.get("trust_level").equals("TRUSTED"))throw new AuthException("ARTIFACT_NOT_ASSIGNED",404);
        var ref=artifact.equals(spec.botA().artifactId())?spec.botA():artifact.equals(spec.botB().artifactId())?spec.botB():null;
        if(ref==null)throw new AuthException("ARTIFACT_NOT_ASSIGNED",404);
        var versions=db.queryForList("select v.artifact_hash,coalesce(v.runtime_language,w.language) as runtime_language from tournament_submission_versions v join tournament_code_workspaces w on w.id=v.workspace_id where v.artifact_id=? and v.status='LOCKED' and w.tournament_id=?",artifact,spec.tournamentId());
        if(versions.size()!=1)throw new AuthException("REMOTE_ARTIFACT_UNAVAILABLE",503);
        var version=versions.getFirst();String physical=version.get("runtime_language").toString(),language=physical.equals("JAR")?"JAVA":physical;
        if(!ref.language().equals(language)||!ref.artifactHash().equals(version.get("artifact_hash")))throw new AuthException("ARTIFACT_HASH_MISMATCH",409);
        try {
            BotLanguage kind=BotLanguage.valueOf(physical);if(!kind.image.equals(ref.runtime()))throw new AuthException("ARTIFACT_RUNTIME_MISMATCH",409);
            Path file=root.resolve("coding").resolve(artifact.toString()).resolve(kind.filename);
            for(Path p=file;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new AuthException("REMOTE_ARTIFACT_UNAVAILABLE",503);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new AuthException("REMOTE_ARTIFACT_UNAVAILABLE",503);
            byte[] bytes;try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=input.readNBytes(WorkspaceSandbox.ARTIFACT_LIMIT+1);}
            if(bytes.length==0||bytes.length>WorkspaceSandbox.ARTIFACT_LIMIT||!WorkspaceSandbox.hash(bytes).equals(ref.artifactHash()))throw new AuthException("ARTIFACT_HASH_MISMATCH",409);
            return new Download(bytes,ref.artifactHash(),language,switch(language){case "PYTHON"->"PYTHON_SOURCE";case "CPP"->"CPP_BINARY";case "JAVA"->"JAVA_JAR";default->throw new IllegalArgumentException();});
        }catch(AuthException e){throw e;}catch(Exception e){throw new AuthException("REMOTE_ARTIFACT_UNAVAILABLE",503);}
    }
}
