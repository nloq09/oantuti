package com.rpsbattle.tournament.coding;

import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.DemoRequestException;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository
public class WorkspaceStore {
 @PersistenceContext EntityManager db;
 public record VersionView(UUID id,int number,String sourceCode,String sourceHash,String artifactHash,String status,String buildLog,String testResult,String sampleResult,Instant createdAt){}
 public record View(UUID id,String language,String sourceCode,long revision,String status,Instant updatedAt,VersionView version){}
 public record Work(UUID workspaceId,UUID versionId,String language,String source,String sourceHash,UUID artifactId,String artifactHash){}
 private CodeWorkspace find(UUID tournament,UUID user,String language,boolean lock){
  var query=db.createQuery("select w from CodeWorkspace w where w.tournamentId=:t and w.userId=:u and w.language=:l",CodeWorkspace.class).setParameter("t",tournament).setParameter("u",user).setParameter("l",language);
  if(lock)query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
  return query.getResultStream().findFirst().orElseThrow(()->new DemoRequestException("WORKSPACE_NOT_FOUND"));
 }
 private View view(CodeWorkspace w){var v=w.currentVersionId==null?null:db.find(CodeVersion.class,w.currentVersionId);return new View(w.id,w.language,w.source,w.revision,w.status,w.updatedAt,v==null?null:new VersionView(v.id,v.number,v.source,v.sourceHash,v.artifactHash,v.status,v.buildLog,v.testResult,v.sampleResult,v.createdAt));}
 @Transactional(readOnly=true) public View read(UUID t,UUID u,String l){return view(find(t,u,l,false));}
 @Transactional public View create(UUID t,UUID u,String language,String template){
  // Lock the account row to serialize concurrent creation of the same unique workspace.
  db.createNativeQuery("select id from users where id=:id for update").setParameter("id",u).getSingleResult();
  var existing=db.createQuery("select w from CodeWorkspace w where w.tournamentId=:t and w.userId=:u and w.language=:l",CodeWorkspace.class).setParameter("t",t).setParameter("u",u).setParameter("l",language).getResultStream().findFirst();
  if(existing.isPresent())return view(existing.get());
  var w=new CodeWorkspace();w.id=UUID.randomUUID();w.tournamentId=t;w.userId=u;w.language=language;w.source=template;w.status="DRAFT";w.createdAt=w.updatedAt=Instant.now();db.persist(w);db.flush();return view(w);
 }
 private void editable(CodeWorkspace w,long revision){if(w.revision!=revision)throw new AuthException("WORKSPACE_CONFLICT",409);if(w.busy())throw new AuthException("WORKSPACE_BUSY",409);}
 @Transactional public View save(UUID t,UUID u,String l,long revision,String source){var w=find(t,u,l,true);editable(w,revision);if(!w.source.equals(source)){w.source=source;w.status="DRAFT";w.currentVersionId=null;w.touch();}return view(w);}
 @Transactional public Work claim(UUID t,UUID u,String l,long revision,String source,int maxVersions,String type){
  // Serialize jobs across all workspaces of this user, including other languages/tournaments.
  db.createNativeQuery("select id from users where id=:id for update").setParameter("id",u).getSingleResult();
  var w=find(t,u,l,true);editable(w,revision);
  long active=db.createQuery("select count(w) from CodeWorkspace w where w.userId=:u and w.status in ('BUILDING','TESTING')",Long.class).setParameter("u",u).getSingleResult();
  if(active>0)throw new AuthException("WORKSPACE_BUSY",409);
  CodeVersion v;
  if(type.equals("BUILD")){
   long count=db.createQuery("select count(v) from CodeVersion v where v.workspaceId=:w",Long.class).setParameter("w",w.id).getSingleResult();
   if(count>=maxVersions)throw new DemoRequestException("WORKSPACE_VERSION_LIMIT");
   v=new CodeVersion();v.id=UUID.randomUUID();v.workspaceId=w.id;v.number=(int)count+1;v.source=source;v.sourceHash=WorkspaceSandbox.hash(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));v.status="BUILDING";v.buildLog="";v.createdAt=v.updatedAt=Instant.now();db.persist(v);db.flush();w.currentVersionId=v.id;
  }else{
   v=w.currentVersionId==null?null:db.find(CodeVersion.class,w.currentVersionId);
   if(v==null||v.artifactId==null||v.artifactHash==null||v.status.equals("BUILD_FAILED"))throw new DemoRequestException("WORKSPACE_BUILD_REQUIRED");
   v.status="TESTING";
  }
  w.status=v.status;w.touch();db.flush();return new Work(w.id,v.id,l,v.source,v.sourceHash,v.artifactId,v.artifactHash);
 }
 @Transactional public void built(Work work,WorkspaceSandbox.Build result){
  var w=db.find(CodeWorkspace.class,work.workspaceId(),LockModeType.PESSIMISTIC_WRITE);var v=db.find(CodeVersion.class,work.versionId());
  if(!Objects.equals(w.currentVersionId,v.id)||!w.status.equals("BUILDING"))return;
  v.status=result.success()?"BUILT":"BUILD_FAILED";v.buildLog=result.log();v.artifactId=result.artifactId();v.artifactHash=result.hash();v.updatedAt=Instant.now();w.status=v.status;w.touch();
 }
 @Transactional public void tested(Work work,String type,String result,boolean passed,boolean hiddenRequired){
  var w=db.find(CodeWorkspace.class,work.workspaceId(),LockModeType.PESSIMISTIC_WRITE);var v=db.find(CodeVersion.class,work.versionId());
  if(!Objects.equals(w.currentVersionId,v.id)||!w.status.equals("TESTING"))return;
  if(type.equals("TEST"))v.testResult=result;else v.sampleResult=result;
  boolean complete=v.testResult!=null&&v.sampleResult!=null&&v.testResult.contains("\"passed\":true")&&v.sampleResult.contains("\"passed\":true");
  v.status=!passed?"TEST_FAILED":complete&&!hiddenRequired?"READY":"BUILT";v.updatedAt=Instant.now();w.status=v.status;w.touch();
 }
 @Transactional public void interrupted(){
  db.createQuery("update CodeVersion v set v.status='BUILD_FAILED',v.buildLog='Job interrupted by server restart; build again.' where v.status='BUILDING'").executeUpdate();
  db.createQuery("update CodeVersion v set v.status='TEST_FAILED' where v.status='TESTING'").executeUpdate();
  db.createQuery("update CodeWorkspace w set w.status='BUILD_FAILED',w.revision=w.revision+1 where w.status='BUILDING'").executeUpdate();
  db.createQuery("update CodeWorkspace w set w.status='TEST_FAILED',w.revision=w.revision+1 where w.status='TESTING'").executeUpdate();
 }
}
