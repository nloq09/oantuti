package com.rpsbattle.tournament.coding;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.rpsbattle.auth.AuthException;
import java.time.Instant;
import java.util.*;

/** Database admission lock and leased jobs: bounded across server instances, fenced completion. */
@Repository
public class CodingQueueStore {
 @PersistenceContext EntityManager db;
 @org.springframework.beans.factory.annotation.Autowired FinalValidationStore finals;
 @org.springframework.beans.factory.annotation.Value("${rps.coding.queue-capacity:200}") int capacity=200;
 private void guard(){db.createNativeQuery("select id from coding_queue_guard where id=1 for update").getSingleResult();}
 @Transactional public void admit(){guard();if(db.createQuery("select count(j) from CodingJob j where j.status in ('QUEUED','RUNNING')",Long.class).getSingleResult()>=capacity)throw new AuthException("WORKSPACE_QUEUE_FULL",429);}
 @Transactional public void enqueue(CodeWorkspace w,CodeVersion v,String kind){enqueue(w,v,kind,null);}
 @Transactional public void enqueue(CodeWorkspace w,CodeVersion v,String kind,UUID attemptId){var j=new CodingJob();j.id=UUID.randomUUID();j.workspaceId=w.id;j.versionId=v.id;j.submissionAttemptId=attemptId;j.kind=kind;j.status="QUEUED";j.createdAt=Instant.now();j.hiddenRequired=policy(w.tournamentId).hiddenTestsEnabled();db.persist(j);}
 private com.rpsbattle.tournament.domain.TournamentBotPolicy policy(UUID id){String raw=(String)db.createNativeQuery("select policy_json from tournament_bot_policies where tournament_id=:id").setParameter("id",id).getSingleResult();return new com.rpsbattle.tournament.persistence.TournamentBotPolicyConverter().convertToEntityAttribute(raw);}
 public record Lease(UUID id,UUID token,String kind,boolean hiddenRequired,WorkspaceStore.Work work,UUID finalValidationId,boolean pipelineSubmission){public Lease(UUID id,UUID token,String kind,boolean hiddenRequired,WorkspaceStore.Work work,UUID finalValidationId){this(id,token,kind,hiddenRequired,work,finalValidationId,false);}}
 @Transactional public Lease lease(){guard();Instant now=Instant.now();
  for(var j:db.createQuery("select j from CodingJob j where j.status='RUNNING' and j.leaseUntil<:now",CodingJob.class).setParameter("now",now).getResultList()){
   if(j.attempts<3){j.status="QUEUED";j.leaseToken=null;j.leaseUntil=null;}else{j.status="FAILED";j.finishedAt=now;if(j.finalValidationId!=null){finals.exhausted(j);continue;}if(j.submissionAttemptId!=null){var a=db.createQuery("select a from SubmissionAttempt a where a.versionId=:v",SubmissionAttempt.class).setParameter("v",j.versionId).getSingleResult();a.status="FAILED";a.errorCode="SUBMISSION_RETRY_EXHAUSTED";a.errorDetail="Máy xử lý bị gián đoạn quá số lần thử. Hãy nộp lại.";a.finishedAt=a.updatedAt=now;var version=db.find(CodeVersion.class,j.versionId);version.status="TEST_FAILED";version.updatedAt=now;continue;}var w=db.find(CodeWorkspace.class,j.workspaceId,LockModeType.PESSIMISTIC_WRITE);var v=db.find(CodeVersion.class,j.versionId);v.status=j.kind.equals("BUILD")?"BUILD_FAILED":"TEST_FAILED";w.status=v.status;w.touch();}}
  db.flush();if(db.createQuery("select count(j) from CodingJob j where j.status='RUNNING'",Long.class).getSingleResult()>=2)return null;
  var candidates=db.createQuery("select j from CodingJob j where j.status='QUEUED' order by j.createdAt,j.id",CodingJob.class).setMaxResults(1).getResultList();if(candidates.isEmpty())return null;var j=candidates.getFirst();j.status="RUNNING";j.leaseToken=UUID.randomUUID();j.leaseUntil=now.plusSeconds(240);j.attempts++;var w=db.find(CodeWorkspace.class,j.workspaceId);var v=db.find(CodeVersion.class,j.versionId);if(j.finalValidationId!=null)finals.claimed(j);if(j.submissionAttemptId!=null){var a=db.createQuery("select a from SubmissionAttempt a where a.versionId=:v",SubmissionAttempt.class).setParameter("v",v.id).getSingleResult();a.status="BUILDING";a.updatedAt=now;}return new Lease(j.id,j.leaseToken,j.kind,j.hiddenRequired,new WorkspaceStore.Work(w.id,v.id,v.runtimeLanguage==null?w.language:v.runtimeLanguage,v.source,v.sourceHash,v.artifactId,v.artifactHash),j.finalValidationId,j.submissionAttemptId!=null);
 }
 @Transactional public void complete(Lease lease,WorkspaceSandbox.Build build,String publicResult,Boolean publicPass,String sampleResult,Boolean samplePass,String hiddenResult,Boolean hiddenPass){
  if(lease.finalValidationId()!=null)throw new IllegalArgumentException("Final jobs require isolated completion");
  guard();var j=db.find(CodingJob.class,lease.id(),LockModeType.PESSIMISTIC_WRITE);if(j==null||!j.status.equals("RUNNING")||!Objects.equals(j.leaseToken,lease.token()))return;
  var w=db.find(CodeWorkspace.class,j.workspaceId,LockModeType.PESSIMISTIC_WRITE);var v=db.find(CodeVersion.class,j.versionId);if(!Objects.equals(w.currentVersionId,v.id)||w.status.equals("LOCKED")){j.status="FAILED";j.finishedAt=Instant.now();return;}
  boolean success=true;
  if(build!=null){v.buildLog=build.log();v.artifactId=build.artifactId();v.artifactHash=build.hash();v.status=build.success()?"BUILT":"BUILD_FAILED";success=build.success();}
  else{
   if(publicPass!=null){v.publicPassed=publicPass;v.testResult=publicResult;success&=publicPass;}
   if(samplePass!=null){v.samplePassed=samplePass;v.sampleResult=sampleResult;success&=samplePass;}
   if(hiddenPass!=null){v.hiddenPassed=hiddenPass;v.hiddenResult=hiddenResult;success&=hiddenPass;}
   boolean ready=v.publicPassed&&v.samplePassed&&(!j.hiddenRequired||v.hiddenPassed);v.status=!success?"TEST_FAILED":ready?"READY":"BUILT";
  }
  for(var s:db.createQuery("select s from BotSubmission s where s.versionId=:v",BotSubmission.class).setParameter("v",v.id).getResultList()){if(!success){if(s.status.equals("LOCKED")){s.status="DISQUALIFIED";v.status="LOCKED";}else s.status="REJECTED";}else if(s.status.equals("LOCKED")&&v.status.equals("READY"))v.status="LOCKED";}
  v.updatedAt=Instant.now();w.status=v.status;w.touch();j.status=success?"DONE":"FAILED";j.finishedAt=Instant.now();
 }
 @Transactional public void completeFinal(Lease lease,boolean passed,boolean infrastructure,String reason,Map<String,String> steps){guard();finals.complete(lease,passed,infrastructure,reason,steps);}
 @Transactional(readOnly=true) public List<Map<String,Object>> jobs(UUID t){return db.createQuery("select j from CodingJob j,CodeWorkspace w where j.workspaceId=w.id and w.tournamentId=:t order by j.createdAt desc",CodingJob.class).setParameter("t",t).setMaxResults(100).getResultStream().map(j->Map.<String,Object>of("id",j.id,"kind",j.kind,"status",j.status,"attempts",j.attempts,"createdAt",j.createdAt,"versionId",j.versionId)).toList();}
}
