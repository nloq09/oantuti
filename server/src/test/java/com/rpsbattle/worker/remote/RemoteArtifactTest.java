package com.rpsbattle.worker.remote;

import com.rpsbattle.bot.upload.BotLanguage;
import com.rpsbattle.tournament.coding.WorkspaceSandbox;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

class RemoteArtifactTest extends RemoteJobClaimTest {
    static final Path ROOT=temporary();
    static Path temporary(){try{return Files.createTempDirectory("rps-remote-artifacts-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void artifacts(DynamicPropertyRegistry r){r.add("rps.bots.directory",()->ROOT.toString());}
    @Autowired RemoteArtifactService artifacts;
    record Fixture(Machine machine,RemoteMatchQueue.Assignment assigned,UUID artifact,Path file){}
    Fixture artifact(UUID actor,String physical,byte[] bytes)throws Exception {
        String language=physical.equals("JAR")?"JAVA":physical;var base=spec(actor,language,language,"TRUSTED");UUID aid=UUID.randomUUID(),workspace=UUID.randomUUID();
        db.update("insert into tournament_code_workspaces(id,tournament_id,user_id,language,source_code,status,created_at,updated_at) values(?,?,?,?,'PRIVATE_WORKSPACE_DO_NOT_SEND','LOCKED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",workspace,base.tournamentId(),actor,language);
        db.update("insert into tournament_submission_versions(id,workspace_id,version_number,source_code,source_hash,artifact_hash,artifact_id,status,build_log,runtime_language,created_at,updated_at) values(?,?,1,'PRIVATE_WORKSPACE_DO_NOT_SEND',?,?,?,'LOCKED','PRIVATE_BUILD_LOG',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),workspace,"a".repeat(64),WorkspaceSandbox.hash(bytes),aid,physical);
        Path file=ROOT.resolve("coding").resolve(aid.toString()).resolve(BotLanguage.valueOf(physical).filename);Files.createDirectories(file.getParent());Files.write(file,bytes);
        var ref=new RemoteMatchQueue.BotRef(aid,WorkspaceSandbox.hash(bytes),language,BotLanguage.valueOf(physical).image);var data=new RemoteMatchQueue.Spec(base.matchId(),base.tournamentId(),ref,base.botB(),"1","classic-online-v1",1,1000,60000,200,"TRUSTED");var machine=machine(actor,Set.of(language),1,"TRUSTED");queue.enqueue(data);var assigned=queue.claim(machine.id(),claim(machine,1)).jobs().getFirst();return new Fixture(machine,assigned,aid,file);
    }
    HttpResponse<byte[]> download(Browser browser,Fixture f,String credential,UUID artifact,UUID execution)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/internal/workers/"+f.machine.id()+"/artifacts/"+artifact+"?executionId="+execution));if(credential!=null)request.header("Authorization","Bearer "+credential);return browser.http.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void assignedCompiledAndPythonArtifactsAreOpaqueAndNeverExposeWorkspaceSourceOrPaths()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);
            for(String physical:List.of("PYTHON","CPP","JAVA","JAR")){
                byte[] bytes=physical.equals("PYTHON")?"print('runtime only')\n".getBytes():physical.equals("CPP")?new byte[]{127,69,76,70,1,2}:new byte[]{80,75,3,4,5,6};var f=artifact(actor,physical,bytes);var response=download(client,f,f.machine.enrolled().workerCredential(),f.artifact,f.assigned.executionId());
                assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isEqualTo(bytes);assertThat(response.headers().firstValue("X-Artifact-SHA256")).hasValue(WorkspaceSandbox.hash(bytes));assertThat(response.headers().firstValue("Content-Type")).hasValue("application/octet-stream");assertThat(new String(response.body())).doesNotContain("PRIVATE_WORKSPACE_DO_NOT_SEND","PRIVATE_BUILD_LOG",ROOT.toString());
                queue.decline(f.machine.id(),f.assigned.jobId(),new RemoteMatchQueue.Decline(f.assigned.executionId(),"EXECUTION_NOT_AVAILABLE"));db.update("update match_execution_jobs set status='CANCELLED' where id=?",f.assigned.jobId());
            }
        }
    }
    @Test void credentialsExecutionAssignmentAndLeaseAreRequiredBeforeArtifactLookup()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);var f=artifact(actor,"PYTHON","print(1)\n".getBytes());var other=machine(actor,Set.of("PYTHON"),1,"TRUSTED");
            assertThat(download(admin,f,null,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(401);
            assertThat(download(client,f,other.enrolled().workerCredential(),f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(403);
            assertThat(download(client,f,f.machine.enrolled().workerCredential(),UUID.randomUUID(),f.assigned.executionId()).statusCode()).isEqualTo(404);
            assertThat(download(client,f,f.machine.enrolled().workerCredential(),f.artifact,UUID.randomUUID()).statusCode()).isEqualTo(404);
            db.update("update workers set trust_level='COMMUNITY' where id=?",f.machine.id());assertThat(download(client,f,f.machine.enrolled().workerCredential(),f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(404);db.update("update workers set trust_level='TRUSTED' where id=?",f.machine.id());
            db.update("update match_execution_attempts set lease_until=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),f.assigned.executionId());assertThat(download(client,f,f.machine.enrolled().workerCredential(),f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(409);
        }
    }
    @Test void tamperUnlockedVersionDeclineAndOversizedFilesNeverReturnArtifactBytes()throws Exception {
        try(var admin=new Browser();var client=new Browser()) {
            UUID actor=admin.account(true);var f=artifact(actor,"CPP",new byte[]{127,69,76,70});String credential=f.machine.enrolled().workerCredential();
            Files.write(f.file,new byte[]{1,2,3});assertThat(download(client,f,credential,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(409);
            Files.write(f.file,new byte[WorkspaceSandbox.ARTIFACT_LIMIT+1]);assertThat(download(client,f,credential,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(409);
            UUID other=UUID.randomUUID();db.update("insert into tournaments(id,owner_user_id,name,format,status) values(?,?,'Other artifact cup','ROUND_ROBIN','DRAFT')",other,actor);db.update("update tournament_code_workspaces set tournament_id=? where id in (select workspace_id from tournament_submission_versions where artifact_id=?)",other,f.artifact);assertThat(download(client,f,credential,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(503);db.update("update tournament_code_workspaces set tournament_id=? where id in (select workspace_id from tournament_submission_versions where artifact_id=?)",f.assigned.match().tournamentId(),f.artifact);
            db.update("update tournament_submission_versions set status='READY' where artifact_id=?",f.artifact);assertThat(download(client,f,credential,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(503);
            queue.decline(f.machine.id(),f.assigned.jobId(),new RemoteMatchQueue.Decline(f.assigned.executionId(),"ARTIFACT_UNAVAILABLE"));assertThat(download(client,f,credential,f.artifact,f.assigned.executionId()).statusCode()).isEqualTo(404);
        }
    }
}
