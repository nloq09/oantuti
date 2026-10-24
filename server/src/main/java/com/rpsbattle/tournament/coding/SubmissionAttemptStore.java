package com.rpsbattle.tournament.coding;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.codevalidation.BotTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Uses the coding queue's admission lock, account lock and lease token on every write. */
@Repository
public class SubmissionAttemptStore {
 @PersistenceContext EntityManager db;
 private final CodingQueueStore queue;
 private final WorkspaceStore workspaces;
 private final SubmissionStore submissions;
 private final ObjectMapper json;
 public SubmissionAttemptStore(CodingQueueStore q,WorkspaceStore w,SubmissionStore s,ObjectMapper j){queue=q;workspaces=w;submissions=s;json=j;}
 public record Snapshot(String language,String source,UUID importedBotId,String runtimeLanguage){}
 public record View(UUID id,UUID versionId,int version,String language,String status,String errorCode,String errorDetail,String buildLog,String testResult,String hiddenResult,Instant createdAt,Instant finishedAt){}
 private View view(SubmissionAttempt a){var v=db.find(CodeVersion.class,a.versionId);var w=db.find(CodeWorkspace.class,v.workspaceId);return new View(a.id,v.id,v.number,w.language,a.status,a.errorCode,a.errorDetail,v.buildLog,v.testResult,v.hiddenResult,a.createdAt,a.finishedAt);}
 @Transactional(readOnly=true) public View latest(UUID t,UUID user){return db.createQuery("select a from SubmissionAttempt a where a.tournamentId=:t and a.userId=:u order by a.createdAt desc,a.id desc",SubmissionAttempt.class).setParameter("t",t).setParameter("u",user).setMaxResults(1).getResultStream().map(this::view).findFirst().orElse(null);}
 @Transactional(readOnly=true) public View existing(UUID id,UUID t,UUID user,Snapshot snapshot){var a=db.find(SubmissionAttempt.class,id);if(a==null)return null;if(!a.tournamentId.equals(t)||!a.userId.equals(user))throw new AuthException("SUBMISSION_REQUEST_CONFLICT",409);var v=db.find(CodeVersion.class,a.versionId);var w=db.find(CodeWorkspace.class,v.workspaceId);if(!w.language.equals(snapshot.language())||!v.source.equals(snapshot.source())||!Objects.equals(v.importedBotId,snapshot.importedBotId()))throw new AuthException("SUBMISSION_REQUEST_CONFLICT",409);return view(a);}
 @Transactional public View start(UUID id,com.rpsbattle.tournament.domain.Tournament t,UUID user,Snapshot snapshot,int limit){
  queue.admit();db.createNativeQuery("select id from users where id=:id for update").setParameter("id",user).getSingleResult();
  UUID tid=UUID.fromString(t.id);var previous=existing(id,tid,user,snapshot);if(previous!=null)return previous;
  requireRevision(t);submissions.requireReplaceable(t,user);
  if(db.createQuery("select count(j) from CodingJob j,CodeWorkspace w where j.workspaceId=w.id and w.userId=:u and j.status in ('QUEUED','RUNNING')",Long.class).setParameter("u",user).getSingleResult()>0)throw new AuthException("WORKSPACE_BUSY",409);
  long total=db.createQuery("select count(v) from CodeVersion v,CodeWorkspace w where v.workspaceId=w.id and w.tournamentId=:t and w.userId=:u",Long.class).setParameter("t",tid).setParameter("u",user).getSingleResult();if(total>=limit)throw new AuthException("WORKSPACE_VERSION_LIMIT",409);
  var created=workspaces.create(tid,user,snapshot.language(),snapshot.source());var w=db.find(CodeWorkspace.class,created.id(),LockModeType.PESSIMISTIC_WRITE);
  int number=((Number)db.createQuery("select count(v) from CodeVersion v where v.workspaceId=:w").setParameter("w",w.id).getSingleResult()).intValue()+1;
  var v=new CodeVersion();v.id=UUID.randomUUID();v.workspaceId=w.id;v.number=number;v.source=snapshot.source();v.sourceHash=WorkspaceSandbox.hash(v.source.getBytes(StandardCharsets.UTF_8));v.importedBotId=snapshot.importedBotId();v.runtimeLanguage=snapshot.runtimeLanguage();v.pipelineSubmission=true;v.status="BUILDING";v.buildLog="";v.createdAt=v.updatedAt=Instant.now();db.persist(v);
  var a=new SubmissionAttempt();a.id=id;a.tournamentId=tid;a.userId=user;a.versionId=v.id;a.status="QUEUED";a.createdAt=a.updatedAt=Instant.now();db.persist(a);db.flush();queue.enqueue(w,v,"BUILD",a.id);db.flush();return view(a);
 }
 private SubmissionAttempt current(CodingQueueStore.Lease lease){db.createNativeQuery("select id from coding_queue_guard where id=1 for update").getSingleResult();var job=db.find(CodingJob.class,lease.id(),LockModeType.PESSIMISTIC_WRITE);if(job==null||!job.status.equals("RUNNING")||!Objects.equals(job.leaseToken,lease.token()))return null;return db.createQuery("select a from SubmissionAttempt a where a.versionId=:v",SubmissionAttempt.class).setParameter("v",job.versionId).getResultStream().filter(a->!Set.of("SUCCEEDED","FAILED").contains(a.status)).findFirst().orElse(null);}
 private void failed(SubmissionAttempt a,CodingQueueStore.Lease lease,String code,String detail){var v=db.find(CodeVersion.class,a.versionId);v.status=a.status.equals("BUILDING")||a.status.equals("QUEUED")?"BUILD_FAILED":"TEST_FAILED";v.updatedAt=Instant.now();a.status="FAILED";a.errorCode=code;a.errorDetail=detail;a.finishedAt=a.updatedAt=Instant.now();var job=db.find(CodingJob.class,lease.id());job.status="FAILED";job.finishedAt=a.finishedAt;}
 @Transactional public void fail(CodingQueueStore.Lease lease,String code,String detail){var a=current(lease);if(a!=null)failed(a,lease,code,detail);}
 @Transactional public WorkspaceStore.Work built(CodingQueueStore.Lease lease,WorkspaceSandbox.Build build){var a=current(lease);if(a==null)return null;var v=db.find(CodeVersion.class,a.versionId);v.buildLog=build.log();v.artifactId=build.artifactId();v.artifactHash=build.hash();v.updatedAt=Instant.now();if(!build.success()){failed(a,lease,"BUILD_FAILED","Build không thành công. "+Objects.toString(build.log(),"").substring(0,Math.min(1000,Objects.toString(build.log(),"").length())));return null;}a.status="TESTING";a.updatedAt=Instant.now();v.status="TESTING";return SubmissionStore.work(db.find(CodeWorkspace.class,v.workspaceId),v);}
 @Transactional public boolean tested(CodingQueueStore.Lease lease,BotTestService.Result result,BotTestService.HiddenResult hidden){var a=current(lease);if(a==null)return false;var v=db.find(CodeVersion.class,a.versionId);try{v.testResult=json.writeValueAsString(result);v.publicPassed=result.passed();if(hidden!=null){v.hiddenResult=json.writeValueAsString(hidden);v.hiddenPassed=hidden.passed();}}catch(Exception error){throw new IllegalStateException(error);}if(!result.passed()){var failedCase=result.cases().stream().filter(c->!"PASS".equals(c.status())).findFirst();String detail=failedCase.map(c->"Lỗi ở bài kiểm thử "+(result.cases().indexOf(c)+1)+": "+c.message()).orElse("Kiểm thử giao thức không đạt.");failed(a,lease,"TEST_FAILED",detail);return false;}if(lease.hiddenRequired()&&(hidden==null||!hidden.passed())){failed(a,lease,"HIDDEN_TEST_FAILED","Kiểm thử bắt buộc của giải không đạt.");return false;}a.status="FINALIZING";a.updatedAt=Instant.now();return true;}
 private void requireRevision(com.rpsbattle.tournament.domain.Tournament t){long revision=((Number)db.createNativeQuery("select revision from tournaments where id=:t for update").setParameter("t",UUID.fromString(t.id)).getSingleResult()).longValue();if(revision!=t.revision)throw new AuthException("STALE_TOURNAMENT_REVISION",409);}
 /** Called inside the same transaction as registration and tournament persistence. */
 @Transactional public boolean finish(CodingQueueStore.Lease lease,com.rpsbattle.tournament.domain.Tournament t,Runnable beforeCommit){var a=current(lease);if(a==null||!a.status.equals("FINALIZING"))throw new AuthException("SUBMISSION_ATTEMPT_STALE",409);requireRevision(t);var v=db.find(CodeVersion.class,a.versionId);v.status="READY";v.updatedAt=Instant.now();submissions.enable(t.id);var registration=submissions.submit(t,a.userId,v.id,false);t.registrations.removeIf(r->r.id().equals(registration.id()));t.registrations.add(registration);for(int i=0;i<t.participants.size();i++){var p=t.participants.get(i);if(p.participantId().equals(registration.id().toString()))t.participants.set(i,p.withHash(registration.artifactHash()));}
  beforeCommit.run();a.status="SUCCEEDED";a.finishedAt=a.updatedAt=Instant.now();var job=db.find(CodingJob.class,lease.id());job.status="DONE";job.finishedAt=a.finishedAt;var w=db.find(CodeWorkspace.class,v.workspaceId);if(!w.status.equals("LOCKED")){w.currentVersionId=v.id;if(!w.source.equals(v.source))w.status="DRAFT";}com.rpsbattle.tournament.TournamentAudit.append(t,"BOT_SUBMITTED",a.userId,registration.id().toString(),null,null,v.id.toString());return true;
 }
 @Transactional(readOnly=true) public View forLease(CodingQueueStore.Lease lease){return db.createQuery("select a from SubmissionAttempt a where a.versionId=:v",SubmissionAttempt.class).setParameter("v",lease.work().versionId()).getResultStream().map(this::view).findFirst().orElse(null);}
 @Transactional(readOnly=true) public UUID user(CodingQueueStore.Lease lease){return db.find(CodeWorkspace.class,lease.work().workspaceId()).userId;}
 @Transactional(readOnly=true) public UUID tournament(CodingQueueStore.Lease lease){return db.find(CodeWorkspace.class,lease.work().workspaceId()).tournamentId;}
}
